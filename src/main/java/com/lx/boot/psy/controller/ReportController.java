package com.lx.boot.psy.controller;

import com.lx.boot.psy.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lx.boot.psy.model.form.ReportForm;
import com.lx.boot.psy.model.query.ReportQuery;
import com.lx.boot.psy.model.vo.ReportVO;
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
 * 测评报告前端控制层
 *
 * @author liuh
 * @since 2025-10-24 14:46
 */
@Tag(name = "测评报告接口")
@RestController
@RequestMapping("/api/v1/report")
@RequiredArgsConstructor
public class ReportController  {

    private final ReportService reportService;

    @Operation(summary = "测评报告分页列表")
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('psy:report:query')")
    public PageResult<ReportVO> getReportPage(ReportQuery queryParams ) {
        IPage<ReportVO> result = reportService.getReportPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "新增测评报告")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('psy:report:add')")
    public Result<Void> saveReport(@RequestBody @Valid ReportForm formData ) {
        boolean result = reportService.saveReport(formData);
        return Result.judge(result);
    }

    @Operation(summary = "获取测评报告表单数据")
    @GetMapping("/{id}/form")
    @PreAuthorize("@ss.hasPerm('psy:report:edit')")
    public Result<ReportForm> getReportForm(
        @Parameter(description = "测评报告ID") @PathVariable Long id
    ) {
        ReportForm formData = reportService.getReportFormData(id);
        return Result.success(formData);
    }

    @Operation(summary = "修改测评报告")
    @PutMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPerm('psy:report:edit')")
    public Result<Void> updateReport(
            @Parameter(description = "测评报告ID") @PathVariable Long id,
            @RequestBody @Validated ReportForm formData
    ) {
        boolean result = reportService.updateReport(id, formData);
        return Result.judge(result);
    }

    @Operation(summary = "删除测评报告")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('psy:report:delete')")
    public Result<Void> deleteReports(
        @Parameter(description = "测评报告ID，多个以英文逗号(,)分割") @PathVariable String ids
    ) {
        boolean result = reportService.deleteReports(ids);
        return Result.judge(result);
    }
}
