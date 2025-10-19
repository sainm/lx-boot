package com.lx.boot.psy.controller;

import com.lx.boot.psy.service.AssessmentRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lx.boot.psy.model.form.AssessmentRecordForm;
import com.lx.boot.psy.model.query.AssessmentRecordQuery;
import com.lx.boot.psy.model.vo.AssessmentRecordVO;
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
 * 测评记录前端控制层
 *
 * @author liuh
 * @since 2025-10-19 18:06
 */
@Tag(name = "测评记录接口")
@RestController
@RequestMapping("/api/v1/assessment-record")
@RequiredArgsConstructor
public class AssessmentRecordController  {

    private final AssessmentRecordService assessmentRecordService;

    @Operation(summary = "测评记录分页列表")
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('psy:assessment-record:query')")
    public PageResult<AssessmentRecordVO> getAssessmentRecordPage(AssessmentRecordQuery queryParams ) {
        IPage<AssessmentRecordVO> result = assessmentRecordService.getAssessmentRecordPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "新增测评记录")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('psy:assessment-record:add')")
    public Result<Void> saveAssessmentRecord(@RequestBody @Valid AssessmentRecordForm formData ) {
        boolean result = assessmentRecordService.saveAssessmentRecord(formData);
        return Result.judge(result);
    }

    @Operation(summary = "获取测评记录表单数据")
    @GetMapping("/{id}/form")
    @PreAuthorize("@ss.hasPerm('psy:assessment-record:edit')")
    public Result<AssessmentRecordForm> getAssessmentRecordForm(
        @Parameter(description = "测评记录ID") @PathVariable Long id
    ) {
        AssessmentRecordForm formData = assessmentRecordService.getAssessmentRecordFormData(id);
        return Result.success(formData);
    }

    @Operation(summary = "修改测评记录")
    @PutMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPerm('psy:assessment-record:edit')")
    public Result<Void> updateAssessmentRecord(
            @Parameter(description = "测评记录ID") @PathVariable Long id,
            @RequestBody @Validated AssessmentRecordForm formData
    ) {
        boolean result = assessmentRecordService.updateAssessmentRecord(id, formData);
        return Result.judge(result);
    }

    @Operation(summary = "删除测评记录")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('psy:assessment-record:delete')")
    public Result<Void> deleteAssessmentRecords(
        @Parameter(description = "测评记录ID，多个以英文逗号(,)分割") @PathVariable String ids
    ) {
        boolean result = assessmentRecordService.deleteAssessmentRecords(ids);
        return Result.judge(result);
    }
}
