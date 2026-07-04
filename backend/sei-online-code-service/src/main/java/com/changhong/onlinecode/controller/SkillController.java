package com.changhong.onlinecode.controller;

import com.changhong.onlinecode.api.SkillApi;
import com.changhong.onlinecode.dto.SkillDto;
import com.changhong.onlinecode.dto.request.ImportSkillRequest;
import com.changhong.onlinecode.entity.Skill;
import com.changhong.onlinecode.service.SkillService;
import com.changhong.sei.core.controller.BaseEntityController;
import com.changhong.sei.core.dto.ResultData;
import com.changhong.sei.core.dto.serach.PageResult;
import com.changhong.sei.core.dto.serach.Search;
import com.changhong.sei.core.service.BaseEntityService;
import com.changhong.sei.core.service.bo.OperateResultWithData;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Skill 管理控制器。实现 {@link SkillApi}，契约 Phase 3 §2 端点 16/17/18/19。
 *
 * @author sei-online-code
 */
@RestController
@Tag(name = "SkillApi", description = "技能管理服务")
@RequestMapping(path = SkillApi.PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class SkillController extends BaseEntityController<Skill, SkillDto>
        implements SkillApi {

    private final SkillService service;

    public SkillController(SkillService service) {
        this.service = service;
    }

    @Override
    public BaseEntityService<Skill> getService() {
        return service;
    }

    @Override
    public ResultData<PageResult<SkillDto>> findByPage(Search search) {
        return convertToDtoPageResult(service.findByPage(search));
    }

    @Override
    public ResultData<SkillDto> importSkill(ImportSkillRequest request) {
        OperateResultWithData<Skill> result = service.importSkill(
                request.getName(),
                request.getDescription(),
                request.getConfig(),
                request.getContent());
        if (result.notSuccessful()) {
            return ResultData.fail(result.getMessage());
        }
        return ResultData.success(convertToDto(result.getData()));
    }
}
