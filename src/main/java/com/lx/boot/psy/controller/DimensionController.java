package com.lx.boot.psy.controller;

import com.lx.boot.psy.service.DimensionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lx.boot.psy.model.form.DimensionForm;
import com.lx.boot.psy.model.query.DimensionQuery;
import com.lx.boot.psy.model.vo.DimensionVO;
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
 * 维度前端控制层
 *
 * @author liuh
 * @since 2025-10-19 18:01
 */
@Tag(name = "维度接口")
@RestController
@RequestMapping("/api/v1/dimension")
@RequiredArgsConstructor
public class DimensionController  {

    private final DimensionService dimensionService;

    @Operation(summary = "维度分页列表")
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('psy:dimension:query')")
    public PageResult<DimensionVO> getDimensionPage(DimensionQuery queryParams ) {
        IPage<DimensionVO> result = dimensionService.getDimensionPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "新增维度")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('psy:dimension:add')")
    public Result<Void> saveDimension(@RequestBody @Valid DimensionForm formData ) {
        boolean result = dimensionService.saveDimension(formData);
        return Result.judge(result);
    }

    @Operation(summary = "获取维度表单数据")
    @GetMapping("/{id}/form")
    @PreAuthorize("@ss.hasPerm('psy:dimension:edit')")
    public Result<DimensionForm> getDimensionForm(
        @Parameter(description = "维度ID") @PathVariable Long id
    ) {
        DimensionForm formData = dimensionService.getDimensionFormData(id);
        return Result.success(formData);
    }

    @Operation(summary = "修改维度")
    @PutMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPerm('psy:dimension:edit')")
    public Result<Void> updateDimension(
            @Parameter(description = "维度ID") @PathVariable Long id,
            @RequestBody @Validated DimensionForm formData
    ) {
        boolean result = dimensionService.updateDimension(id, formData);
        return Result.judge(result);
    }

    @Operation(summary = "删除维度")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('psy:dimension:delete')")
    public Result<Void> deleteDimensions(
        @Parameter(description = "维度ID，多个以英文逗号(,)分割") @PathVariable String ids
    ) {
        boolean result = dimensionService.deleteDimensions(ids);
        return Result.judge(result);
    }
}
