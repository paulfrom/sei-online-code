package com.changhong.onlinecode.service.support;

import com.changhong.onlinecode.exception.InvalidSkillImportException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillArchiveSupportTest {

    @Test
    void parseArchive_extractsRootSkillAndAuxFiles() throws IOException {
        byte[] zip = buildZip(Map.of(
                "review-helper/SKILL.md", "---\nname: review-helper\ndescription: Reviews code\n---\n\n# Review\n",
                "review-helper/references/guide.md", "guide",
                "review-helper/scripts/run.sh", "echo hi"
        ));

        SkillArchiveSupport.ParsedSkill parsed = SkillArchiveSupport.parseArchive(
                zip, "review-helper.zip", null, "local:review-helper");

        assertEquals("review-helper", parsed.getName());
        assertEquals("Reviews code", parsed.getDescription());
        assertEquals(2, parsed.getFiles().size());
        assertEquals("references/guide.md", parsed.getFiles().get(0).getPath());
        assertEquals("scripts/run.sh", parsed.getFiles().get(1).getPath());
    }

    @Test
    void parseArchive_prefersRequestedGithubPath() throws IOException {
        byte[] zip = buildZip(Map.of(
                "repo-main/alpha/SKILL.md", "---\nname: alpha\n---\n",
                "repo-main/beta/SKILL.md", "---\nname: beta\n---\n",
                "repo-main/beta/references/doc.md", "beta"
        ));

        SkillArchiveSupport.ParsedSkill parsed = SkillArchiveSupport.parseArchive(
                zip, "repo-main.zip", "beta", "github:acme/repo/beta#main");

        assertEquals("beta", parsed.getName());
        assertEquals(1, parsed.getFiles().size());
        assertEquals("references/doc.md", parsed.getFiles().get(0).getPath());
    }

    @Test
    void parseArchive_rejectsInvalidSkillName() throws IOException {
        byte[] zip = buildZip(Map.of(
                "demo/SKILL.md", "---\nname: DemoSkill\n---\n"
        ));

        assertThrows(InvalidSkillImportException.class,
                () -> SkillArchiveSupport.parseArchive(zip, "demo.zip", null, "local:demo"));
    }

    @Test
    void parseGitHubUrl_supportsTreeAndBlobForms() {
        SkillArchiveSupport.GitHubImportTarget tree = SkillArchiveSupport.parseGitHubUrl(
                "https://github.com/acme/skills/tree/main/review-helper");
        assertEquals("main", tree.getRef());
        assertEquals("review-helper", tree.getSkillPath());
        assertEquals("github:acme/skills/review-helper#main", tree.getOrigin());

        SkillArchiveSupport.GitHubImportTarget blob = SkillArchiveSupport.parseGitHubUrl(
                "https://github.com/acme/skills/blob/main/review-helper/SKILL.md");
        assertEquals("review-helper", blob.getSkillPath());
    }

    private static byte[] buildZip(Map<String, String> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
