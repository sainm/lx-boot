package com.lx.boot.psy.controller;

import com.lx.boot.psy.service.ScaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lx.boot.psy.model.form.ScaleForm;
import com.lx.boot.psy.model.query.ScaleQuery;
import com.lx.boot.psy.model.vo.ScaleVO;
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
 * 量主前端控制层
 *
 * @author liuh
 * @since 2025-10-19 18:02
 */
@Tag(name = "量主接口")
@RestController
@RequestMapping("/api/v1/scale")
@RequiredArgsConstructor
public class ScaleController  {

    private final ScaleService scaleService;

    @Operation(summary = "量主分页列表")
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('psy:scale:query')")
    public PageResult<ScaleVO> getScalePage(ScaleQuery queryParams ) {
        IPage<ScaleVO> result = scaleService.getScalePage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "新增量主")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('psy:scale:add')")
    public Result<Void> saveScale(@RequestBody @Valid ScaleForm formData ) {
        boolean result = scaleService.saveScale(formData);
        return Result.judge(result);
    }

    @Operation(summary = "获取量主表单数据")
    @GetMapping("/{id}/form")
    @PreAuthorize("@ss.hasPerm('psy:scale:edit')")
    public Result<ScaleForm> getScaleForm(
        @Parameter(description = "量主ID") @PathVariable Long id
    ) {
        ScaleForm formData = scaleService.getScaleFormData(id);
        return Result.success(formData);
    }

    @Operation(summary = "修改量主")
    @PutMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPerm('psy:scale:edit')")
    public Result<Void> updateScale(
            @Parameter(description = "量主ID") @PathVariable Long id,
            @RequestBody @Validated ScaleForm formData
    ) {
        boolean result = scaleService.updateScale(id, formData);
        return Result.judge(result);
    }

    @Operation(summary = "删除量主")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('psy:scale:delete')")
    public Result<Void> deleteScales(
        @Parameter(description = "量主ID，多个以英文逗号(,)分割") @PathVariable String ids
    ) {
        boolean result = scaleService.deleteScales(ids);
        return Result.judge(result);
    }
}
