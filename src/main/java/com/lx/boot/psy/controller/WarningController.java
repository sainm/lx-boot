package com.lx.boot.psy.controller;

import com.lx.boot.psy.service.WarningService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lx.boot.psy.model.form.WarningForm;
import com.lx.boot.psy.model.query.WarningQuery;
import com.lx.boot.psy.model.vo.WarningVO;
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
 * 测评预警记录前端控制层
 *
 * @author liuh
 * @since 2025-10-19 18:04
 */
@Tag(name = "测评预警记录接口")
@RestController
@RequestMapping("/api/v1/warning")
@RequiredArgsConstructor
public class WarningController  {

    private final WarningService warningService;

    @Operation(summary = "测评预警记录分页列表")
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('psy:warning:query')")
    public PageResult<WarningVO> getWarningPage(WarningQuery queryParams ) {
        IPage<WarningVO> result = warningService.getWarningPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "新增测评预警记录")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('psy:warning:add')")
    public Result<Void> saveWarning(@RequestBody @Valid WarningForm formData ) {
        boolean result = warningService.saveWarning(formData);
        return Result.judge(result);
    }

    @Operation(summary = "获取测评预警记录表单数据")
    @GetMapping("/{id}/form")
    @PreAuthorize("@ss.hasPerm('psy:warning:edit')")
    public Result<WarningForm> getWarningForm(
        @Parameter(description = "测评预警记录ID") @PathVariable Long id
    ) {
        WarningForm formData = warningService.getWarningFormData(id);
        return Result.success(formData);
    }

    @Operation(summary = "修改测评预警记录")
    @PutMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPerm('psy:warning:edit')")
    public Result<Void> updateWarning(
            @Parameter(description = "测评预警记录ID") @PathVariable Long id,
            @RequestBody @Validated WarningForm formData
    ) {
        boolean result = warningService.updateWarning(id, formData);
        return Result.judge(result);
    }

    @Operation(summary = "删除测评预警记录")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('psy:warning:delete')")
    public Result<Void> deleteWarnings(
        @Parameter(description = "测评预警记录ID，多个以英文逗号(,)分割") @PathVariable String ids
    ) {
        boolean result = warningService.deleteWarnings(ids);
        return Result.judge(result);
    }
}
