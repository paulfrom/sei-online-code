package com.changhong.onlinecode.service;

import com.changhong.onlinecode.dto.skill.SkillConfig;
import com.changhong.onlinecode.exception.InvalidSkillImportException;
import com.changhong.onlinecode.service.support.SkillArchiveSupport;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Skill 导入解析服务。
 *
 * <p>负责从 GitHub 地址或上传归档中提取结构化 skill，再交由 {@link SkillService} 落库。</p>
 *
 * @author sei-online-code
 */
@Service
public class SkillImportService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public SkillArchiveSupport.ParsedSkill parseArchive(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidSkillImportException("请上传 .zip 或 .skill 文件");
        }
        try {
            return SkillArchiveSupport.parseArchive(file.getBytes(), file.getOriginalFilename(), null,
                    "local:" + fallbackArchiveOrigin(file.getOriginalFilename()));
        } catch (IOException e) {
            throw new InvalidSkillImportException("读取上传的技能归档失败");
        }
    }

    public SkillArchiveSupport.ParsedSkill importFromGithub(String url) {
        SkillArchiveSupport.GitHubImportTarget target = SkillArchiveSupport.parseGitHubUrl(url);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(target.getArchiveUrl()))
                .header("Accept", "application/zip")
                .GET()
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != HttpStatus.OK.value()) {
                throw new InvalidSkillImportException("GitHub skill 下载失败，HTTP " + response.statusCode());
            }
            return SkillArchiveSupport.parseArchive(response.body(),
                    target.getRepo() + "-" + target.getRef() + ".zip",
                    target.getSkillPath(),
                    target.getOrigin());
        } catch (IOException e) {
            throw new InvalidSkillImportException("下载 GitHub skill 失败");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InvalidSkillImportException("下载 GitHub skill 被中断");
        }
    }

    public SkillConfig toConfig(SkillArchiveSupport.ParsedSkill parsedSkill) {
        return new SkillConfig(parsedSkill.getOrigin());
    }

    private String fallbackArchiveOrigin(String filename) {
        if (filename == null || filename.isBlank()) {
            return "archive";
        }
        String base = filename.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }
}
