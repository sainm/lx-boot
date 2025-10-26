package com.lx.boot.psy.controller;

import com.lx.boot.psy.service.AssessmentAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lx.boot.psy.model.form.AssessmentAssignmentForm;
import com.lx.boot.psy.model.query.AssessmentAssignmentQuery;
import com.lx.boot.psy.model.vo.AssessmentAssignmentVO;
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
 * 测评任务分配前端控制层
 *
 * @author liuh
 * @since 2025-10-23 22:34
 */
@Tag(name = "测评任务分配接口")
@RestController
@RequestMapping("/api/v1/assessment-assignment")
@RequiredArgsConstructor
public class AssessmentAssignmentController  {

    private final AssessmentAssignmentService assessmentAssignmentService;

    @Operation(summary = "测评任务分配分页列表")
    @GetMapping({"/page", "/my-tasks"})
    @PreAuthorize("@ss.hasPerm('psym:assessment-assignment:query')")
    public PageResult<AssessmentAssignmentVO> getAssessmentAssignmentPage(AssessmentAssignmentQuery queryParams ) {
        IPage<AssessmentAssignmentVO> result = assessmentAssignmentService.getAssessmentAssignmentPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "新增测评任务分配")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('psym:assessment-assignment:add')")
    public Result<Void> saveAssessmentAssignment(@RequestBody @Valid AssessmentAssignmentForm formData ) {
        boolean result = assessmentAssignmentService.saveAssessmentAssignment(formData);
        return Result.judge(result);
    }

    @Operation(summary = "获取测评任务分配表单数据")
    @GetMapping("/{id}/form")
    @PreAuthorize("@ss.hasPerm('psym:assessment-assignment:edit')")
    public Result<AssessmentAssignmentForm> getAssessmentAssignmentForm(
        @Parameter(description = "测评任务分配ID") @PathVariable Long id
    ) {
        AssessmentAssignmentForm formData = assessmentAssignmentService.getAssessmentAssignmentFormData(id);
        return Result.success(formData);
    }

    @Operation(summary = "修改测评任务分配")
    @PutMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPerm('psym:assessment-assignment:edit')")
    public Result<Void> updateAssessmentAssignment(
            @Parameter(description = "测评任务分配ID") @PathVariable Long id,
            @RequestBody @Validated AssessmentAssignmentForm formData
    ) {
        boolean result = assessmentAssignmentService.updateAssessmentAssignment(id, formData);
        return Result.judge(result);
    }

    @Operation(summary = "删除测评任务分配")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('psym:assessment-assignment:delete')")
    public Result<Void> deleteAssessmentAssignments(
        @Parameter(description = "测评任务分配ID，多个以英文逗号(,)分割") @PathVariable String ids
    ) {
        boolean result = assessmentAssignmentService.deleteAssessmentAssignments(ids);
        return Result.judge(result);
    }


//    public PageResult<AssessmentAssignmentVO> getTasks(AssessmentAssignmentQuery queryParams){
//        IPage<AssessmentAssignmentVO> result = assessmentAssignmentService.getAssessmentAssignmentPage(queryParams);
//        return PageResult.success(result);
//    }

}
