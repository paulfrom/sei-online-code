package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.SkillApi;
import com.changhong.onlinecode.dto.SkillDto;
import com.changhong.onlinecode.dto.request.ImportGithubSkillRequest;
import com.changhong.onlinecode.dto.request.ImportSkillRequest;
import com.changhong.onlinecode.entity.Skill;
import com.changhong.onlinecode.service.SkillImportService;
import com.changhong.onlinecode.service.SkillService;
import com.changhong.onlinecode.service.support.SkillArchiveSupport;
import com.changhong.sei.core.controller.BaseEntityController;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.dto.serach.PageResult;
import com.changhong.sei.core.dto.serach.Search;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Skill 管理控制器。实现 {@link SkillApi}，契约 Phase 3 §2 端点 16/17/18/19。
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "SkillApi", description = "技能管理服务")
@RequestMapping(path = SkillApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@AllArgsConstructor
public class SkillController extends BaseEntityController<Skill, SkillDto>
        implements SkillApi {

    private final SkillService service;
    private final SkillImportService importService;

    @Override
    public BaseEntityService<Skill> getService() {
        return service;
    }

    @Override
    public ResultData<PageResult<SkillDto>> findByPage(Search search) {
        return convertToDtoPageResult(service.findByPage(search));
    }

    @Override
    @PostMapping(path = "import", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResultData<SkillDto> importSkill(@RequestBody ImportSkillRequest request) {
        OperateResultWithData<Skill> result = service.importSkill(
                request.getName(),
                request.getDescription(),
                request.getConfig(),
                request.getContent(),
                request.getFiles());
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(convertToDto(result.getData()));
    }

    @Override
    @PostMapping(path = "import/github", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResultData<SkillDto> importGithubSkill(@RequestBody ImportGithubSkillRequest request) {
        SkillArchiveSupport.ParsedSkill parsedSkill = importService.importFromGithub(request.getUrl());
        return doImport(parsedSkill);
    }

    @Override
    @PostMapping(path = "import/archive", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResultData<SkillDto> importArchiveSkill(@RequestPart("file") MultipartFile file) {
        SkillArchiveSupport.ParsedSkill parsedSkill = importService.parseArchive(file);
        return doImport(parsedSkill);
    }

    private ResultData<SkillDto> doImport(SkillArchiveSupport.ParsedSkill parsedSkill) {
        OperateResultWithData<Skill> result = service.importSkill(
                parsedSkill.getName(),
                parsedSkill.getDescription(),
                importService.toConfig(parsedSkill),
                parsedSkill.getContent(),
                parsedSkill.getFiles());
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(convertToDto(result.getData()));
    }
}
