package com.lx.boot.psy.controller;

import com.lx.boot.psy.service.ScaleVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lx.boot.psy.model.form.ScaleVersionForm;
import com.lx.boot.psy.model.query.ScaleVersionQuery;
import com.lx.boot.psy.model.vo.ScaleVersionVO;
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
 * 量版本前端控制层
 *
 * @author liuh
 * @since 2025-10-19 18:02
 */
@Tag(name = "量版本接口")
@RestController
@RequestMapping("/api/v1/scale-version")
@RequiredArgsConstructor
public class ScaleVersionController  {

    private final ScaleVersionService scaleVersionService;

    @Operation(summary = "量版本分页列表")
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('psy:scale-version:query')")
    public PageResult<ScaleVersionVO> getScaleVersionPage(ScaleVersionQuery queryParams ) {
        IPage<ScaleVersionVO> result = scaleVersionService.getScaleVersionPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "新增量版本")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('psy:scale-version:add')")
    public Result<Void> saveScaleVersion(@RequestBody @Valid ScaleVersionForm formData ) {
        boolean result = scaleVersionService.saveScaleVersion(formData);
        return Result.judge(result);
    }

    @Operation(summary = "获取量版本表单数据")
    @GetMapping("/{id}/form")
    @PreAuthorize("@ss.hasPerm('psy:scale-version:edit')")
    public Result<ScaleVersionForm> getScaleVersionForm(
        @Parameter(description = "量版本ID") @PathVariable Long id
    ) {
        ScaleVersionForm formData = scaleVersionService.getScaleVersionFormData(id);
        return Result.success(formData);
    }

    @Operation(summary = "修改量版本")
    @PutMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPerm('psy:scale-version:edit')")
    public Result<Void> updateScaleVersion(
            @Parameter(description = "量版本ID") @PathVariable Long id,
            @RequestBody @Validated ScaleVersionForm formData
    ) {
        boolean result = scaleVersionService.updateScaleVersion(id, formData);
        return Result.judge(result);
    }

    @Operation(summary = "删除量版本")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('psy:scale-version:delete')")
    public Result<Void> deleteScaleVersions(
        @Parameter(description = "量版本ID，多个以英文逗号(,)分割") @PathVariable String ids
    ) {
        boolean result = scaleVersionService.deleteScaleVersions(ids);
        return Result.judge(result);
    }
}
