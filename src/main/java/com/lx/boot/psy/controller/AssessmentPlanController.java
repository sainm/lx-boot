package com.lx.boot.psy.controller;

import com.lx.boot.psy.service.AssessmentPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lx.boot.psy.model.form.AssessmentPlanForm;
import com.lx.boot.psy.model.query.AssessmentPlanQuery;
import com.lx.boot.psy.model.vo.AssessmentPlanVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lx.boot.common.result.PageResult;
import com.lx.boot.common.result.Result;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

/**
 * 测评计划前端控制层
 *
 * @author liuh
 * @since 2025-10-24 14:39
 */
@Tag(name = "测评计划接口")
@RestController
@RequestMapping("/api/v1/assessment-plan")
@RequiredArgsConstructor
public class AssessmentPlanController  {

    private final AssessmentPlanService assessmentPlanService;

    @Operation(summary = "测评计划分页列表")
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('psym:assessment-plan:query')")
    public PageResult<AssessmentPlanVO> getAssessmentPlanPage(AssessmentPlanQuery queryParams ) {
        IPage<AssessmentPlanVO> result = assessmentPlanService.getAssessmentPlanPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "新增测评计划")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('psym:assessment-plan:add')")
    public Result<Void> saveAssessmentPlan(@RequestBody @Valid AssessmentPlanForm formData ) {
        boolean result = assessmentPlanService.saveAssessmentPlan(formData);
        return Result.judge(result);
    }

    @Operation(summary = "获取测评计划表单数据")
    @GetMapping("/{id}/form")
    @PreAuthorize("@ss.hasPerm('psym:assessment-plan:edit')")
    public Result<AssessmentPlanForm> getAssessmentPlanForm(
        @Parameter(description = "测评计划ID") @PathVariable Long id
    ) {
        AssessmentPlanForm formData = assessmentPlanService.getAssessmentPlanFormData(id);
        return Result.success(formData);
    }

    @Operation(summary = "修改测评计划")
    @PutMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPerm('psym:assessment-plan:edit')")
    public Result<Void> updateAssessmentPlan(
            @Parameter(description = "测评计划ID") @PathVariable Long id,
            @RequestBody @Validated AssessmentPlanForm formData
    ) {
        boolean result = assessmentPlanService.updateAssessmentPlan(id, formData);
        return Result.judge(result);
    }

    @Operation(summary = "删除测评计划")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('psym:assessment-plan:delete')")
    public Result<Void> deleteAssessmentPlans(
        @Parameter(description = "测评计划ID，多个以英文逗号(,)分割") @PathVariable String ids
    ) {
        boolean result = assessmentPlanService.deleteAssessmentPlans(ids);
        return Result.judge(result);
    }
}
