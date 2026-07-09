package com.changhong.onlinecode.service.support;

import com.changhong.onlinecode.dto.skill.SkillFileDto;
import com.changhong.onlinecode.exception.InvalidSkillImportException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 技能导入辅助工具。
 *
 * <p>参考 multica skill archive import：从 zip/.skill 或 GitHub repo 归档中抽取
 * {@code SKILL.md} 与辅助文件，过滤越界路径、点文件、license、二进制内容。</p>
 *
 * @author sei-online-code
 */
public final class SkillArchiveSupport {

    public static final long MAX_ARCHIVE_SIZE = 16L * 1024 * 1024;
    public static final int MAX_ENTRY_SIZE = 512 * 1024;
    public static final int MAX_FILE_COUNT = 256;
    public static final int MAX_TOTAL_TEXT_SIZE = 2 * 1024 * 1024;
    public static final Pattern SKILL_NAME_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
    private static final String SKILL_MD = "SKILL.md";
    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
            "^---\\s*\\R(.*?)\\R---\\s*(?:\\R|$)", Pattern.DOTALL);

    private SkillArchiveSupport() {
    }

    public static ParsedSkill parseArchive(byte[] data, String filename, String requestedPath, String origin) {
        List<ArchiveEntryData> entries = readArchive(data);
        if (entries.isEmpty()) {
            throw new InvalidSkillImportException("技能归档为空或不包含可读文件");
        }
        String normalizedRequested = normalizeRequestedPath(requestedPath);
        ArchiveEntryData skillEntry = chooseSkillEntry(entries, normalizedRequested);
        if (skillEntry == null) {
            throw new InvalidSkillImportException("技能归档中未找到有效的 SKILL.md");
        }
        SkillFrontmatter frontmatter = parseFrontmatter(skillEntry.content);
        String rootPrefix = parentOf(skillEntry.normalizedName);
        String name = firstNonBlank(frontmatter.name,
                fallbackName(normalizedRequested, rootPrefix, filename));
        if (!isValidSkillName(name)) {
            throw new InvalidSkillImportException(
                    "技能名不合法: " + name + "，需匹配 ^[a-z0-9][a-z0-9-]{0,63}$");
        }

        List<SkillFileDto> files = new ArrayList<>();
        int total = 0;
        for (ArchiveEntryData entry : entries) {
            if (entry == skillEntry) {
                continue;
            }
            if (!isUnderRoot(entry.normalizedName, rootPrefix)) {
                continue;
            }
            String relative = rootPrefix.isEmpty()
                    ? entry.normalizedName
                    : entry.normalizedName.substring(rootPrefix.length());
            if (relative.isBlank() || SKILL_MD.equalsIgnoreCase(baseName(relative)) || isIgnored(relative)) {
                continue;
            }
            if (!isUtf8Text(entry.raw)) {
                continue;
            }
            total += entry.content.length();
            if (files.size() >= MAX_FILE_COUNT || total > MAX_TOTAL_TEXT_SIZE) {
                throw new InvalidSkillImportException("技能辅助文件过多或总大小超限");
            }
            files.add(new SkillFileDto(relative, entry.content));
        }
        files.sort(Comparator.comparing(SkillFileDto::getPath));
        return new ParsedSkill(name, firstNonBlank(frontmatter.description, ""), origin, skillEntry.content, files);
    }

    public static GitHubImportTarget parseGitHubUrl(String url) {
        try {
            URI uri = new URI(Objects.requireNonNull(url, "url"));
            String host = uri.getHost();
            if (host == null || !(host.equalsIgnoreCase("github.com") || host.equalsIgnoreCase("www.github.com"))) {
                throw new InvalidSkillImportException("仅支持 github.com 地址导入技能");
            }
            String[] parts = uri.getPath().replaceAll("^/+", "").split("/");
            if (parts.length < 2) {
                throw new InvalidSkillImportException("GitHub 地址至少需要 owner/repo");
            }
            String owner = parts[0];
            String repo = parts[1];
            String ref = "HEAD";
            String skillPath = "";
            if (parts.length >= 4 && ("tree".equals(parts[2]) || "blob".equals(parts[2]))) {
                ref = parts[3];
                if (parts.length > 4) {
                    skillPath = join(parts, 4);
                }
                if ("blob".equals(parts[2])) {
                    if (!skillPath.endsWith("/" + SKILL_MD) && !skillPath.equals(SKILL_MD)) {
                        throw new InvalidSkillImportException("GitHub blob 地址必须指向 SKILL.md");
                    }
                    skillPath = parentOf(skillPath);
                }
            } else if (parts.length > 2) {
                skillPath = join(parts, 2);
            }
            skillPath = normalizeRequestedPath(skillPath);
            String archiveUrl = "https://github.com/" + owner + "/" + repo + "/archive/" + ref + ".zip";
            String origin = "github:" + owner + "/" + repo
                    + (skillPath.isBlank() ? "" : "/" + skillPath)
                    + ("HEAD".equals(ref) ? "" : "#" + ref);
            return new GitHubImportTarget(owner, repo, ref, skillPath, archiveUrl, origin);
        } catch (URISyntaxException | NullPointerException e) {
            throw new InvalidSkillImportException("GitHub 地址格式不正确");
        }
    }

    public static boolean isValidSkillName(String name) {
        return name != null && SKILL_NAME_PATTERN.matcher(name).matches();
    }

    private static List<ArchiveEntryData> readArchive(byte[] data) {
        if (data == null || data.length == 0) {
            throw new InvalidSkillImportException("技能归档不能为空");
        }
        if (data.length > MAX_ARCHIVE_SIZE) {
            throw new InvalidSkillImportException("技能归档大小超限，最大 16MB");
        }
        List<ArchiveEntryData> entries = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String normalized = normalizeEntryName(entry.getName());
                if (normalized == null) {
                    continue;
                }
                byte[] raw = readLimited(zis, MAX_ENTRY_SIZE);
                String content = decodeUtf8(raw);
                if (content == null) {
                    continue;
                }
                entries.add(new ArchiveEntryData(normalized, raw, content));
            }
        } catch (IOException e) {
            throw new InvalidSkillImportException("归档不是合法的 zip/.skill 文件");
        }
        return entries;
    }

    private static ArchiveEntryData chooseSkillEntry(List<ArchiveEntryData> entries, String requestedPath) {
        ArchiveEntryData preferred = null;
        ArchiveEntryData shallowest = null;
        for (ArchiveEntryData entry : entries) {
            if (!SKILL_MD.equalsIgnoreCase(baseName(entry.normalizedName))) {
                continue;
            }
            String repoRelative = stripFirstSegment(entry.normalizedName);
            String candidateDir = normalizeRequestedPath(parentOf(repoRelative));
            if (requestedPath != null && !requestedPath.isBlank() && requestedPath.equals(candidateDir)) {
                preferred = entry;
                break;
            }
            if (requestedPath != null && requestedPath.isBlank() && repoRelative.equals(SKILL_MD)) {
                preferred = entry;
                break;
            }
            if (shallowest == null || depth(entry.normalizedName) < depth(shallowest.normalizedName)) {
                shallowest = entry;
            }
        }
        return preferred != null ? preferred : shallowest;
    }

    private static String normalizeRequestedPath(String requestedPath) {
        if (requestedPath == null || requestedPath.isBlank()) {
            return "";
        }
        String normalized = requestedPath.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.contains("..")) {
            throw new InvalidSkillImportException("技能路径不能包含 ..");
        }
        return normalized;
    }

    private static String normalizeEntryName(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank() || normalized.contains("../") || normalized.startsWith("../") || normalized.contains("/..")) {
            return null;
        }
        return normalized;
    }

    private static byte[] readLimited(InputStream inputStream, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new InvalidSkillImportException("技能文件单文件大小超限");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String decodeUtf8(byte[] raw) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            CharBuffer buffer = decoder.decode(ByteBuffer.wrap(raw));
            return buffer.toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static boolean isUtf8Text(byte[] raw) {
        return decodeUtf8(raw) != null;
    }

    private static SkillFrontmatter parseFrontmatter(String content) {
        if (content == null) {
            return new SkillFrontmatter(null, null);
        }
        Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
        if (!matcher.find()) {
            return new SkillFrontmatter(null, null);
        }
        String name = null;
        String description = null;
        String[] lines = matcher.group(1).split("\\R");
        for (String line : lines) {
            int idx = line.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String key = line.substring(0, idx).trim().toLowerCase(Locale.ROOT);
            String value = stripQuotes(line.substring(idx + 1).trim());
            if ("name".equals(key)) {
                name = value;
            } else if ("description".equals(key)) {
                description = value;
            }
        }
        return new SkillFrontmatter(name, description);
    }

    private static String stripQuotes(String value) {
        if (value == null || value.length() < 2) {
            return value;
        }
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String fallbackName(String requestedPath, String rootPrefix, String filename) {
        if (requestedPath != null && !requestedPath.isBlank()) {
            return baseName(requestedPath);
        }
        String repoRelativeRoot = stripFirstSegment(rootPrefix);
        if (!repoRelativeRoot.isBlank()) {
            return baseName(repoRelativeRoot);
        }
        if (filename == null || filename.isBlank()) {
            return null;
        }
        String base = filename.replace('\\', '/');
        base = baseName(base);
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    private static boolean isUnderRoot(String candidate, String rootPrefix) {
        if (rootPrefix == null || rootPrefix.isBlank()) {
            return true;
        }
        return candidate.startsWith(rootPrefix);
    }

    private static boolean isIgnored(String relativePath) {
        String[] segments = relativePath.split("/");
        for (String segment : segments) {
            if (segment.isBlank() || segment.startsWith(".") || "__MACOSX".equals(segment)) {
                return true;
            }
        }
        String base = baseName(relativePath).toLowerCase(Locale.ROOT);
        return "license".equals(base) || "license.md".equals(base) || "license.txt".equals(base);
    }

    private static String parentOf(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String normalized = path;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int idx = normalized.lastIndexOf('/');
        return idx < 0 ? "" : normalized.substring(0, idx + 1);
    }

    private static String baseName(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String normalized = path;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int idx = normalized.lastIndexOf('/');
        return idx < 0 ? normalized : normalized.substring(idx + 1);
    }

    private static int depth(String path) {
        if (path == null || path.isBlank()) {
            return 0;
        }
        int depth = 0;
        for (String ignored : path.split("/")) {
            depth++;
        }
        return depth;
    }

    private static String stripFirstSegment(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String normalized = path;
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        int idx = normalized.indexOf('/');
        return idx < 0 ? "" : normalized.substring(idx + 1);
    }

    private static String join(String[] parts, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < parts.length; i++) {
            if (parts[i] == null || parts[i].isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    public static final class ParsedSkill {
        private final String name;
        private final String description;
        private final String origin;
        private final String content;
        private final List<SkillFileDto> files;

        public ParsedSkill(String name, String description, String origin, String content, List<SkillFileDto> files) {
            this.name = name;
            this.description = description;
            this.origin = origin;
            this.content = content;
            this.files = files;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getOrigin() {
            return origin;
        }

        public String getContent() {
            return content;
        }

        public List<SkillFileDto> getFiles() {
            return files;
        }
    }

    public static final class GitHubImportTarget {
        private final String owner;
        private final String repo;
        private final String ref;
        private final String skillPath;
        private final String archiveUrl;
        private final String origin;

        public GitHubImportTarget(String owner, String repo, String ref, String skillPath, String archiveUrl,
                                  String origin) {
            this.owner = owner;
            this.repo = repo;
            this.ref = ref;
            this.skillPath = skillPath;
            this.archiveUrl = archiveUrl;
            this.origin = origin;
        }

        public String getOwner() {
            return owner;
        }

        public String getRepo() {
            return repo;
        }

        public String getRef() {
            return ref;
        }

        public String getSkillPath() {
            return skillPath;
        }

        public String getArchiveUrl() {
            return archiveUrl;
        }

        public String getOrigin() {
            return origin;
        }
    }

    private static final class ArchiveEntryData {
        private final String normalizedName;
        private final byte[] raw;
        private final String content;

        private ArchiveEntryData(String normalizedName, byte[] raw, String content) {
            this.normalizedName = normalizedName;
            this.raw = raw;
            this.content = content;
        }
    }

    private static final class SkillFrontmatter {
        private final String name;
        private final String description;

        private SkillFrontmatter(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }
}
