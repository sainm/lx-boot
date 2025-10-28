package com.lx.boot.psy.controller;

import com.lx.boot.psy.service.NormSampleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lx.boot.psy.model.form.NormSampleForm;
import com.lx.boot.psy.model.query.NormSampleQuery;
import com.lx.boot.psy.model.vo.NormSampleVO;
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
 * 量常模样本定义前端控制层
 *
 * @author liuh
 * @since 2025-10-28 12:27
 */
@Tag(name = "量常模样本定义接口")
@RestController
@RequestMapping("/api/v1/norm-sample")
@RequiredArgsConstructor
public class NormSampleController  {

    private final NormSampleService normSampleService;

    @Operation(summary = "量常模样本定义分页列表")
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('psy:norm-sample:query')")
    public PageResult<NormSampleVO> getNormSamplePage(NormSampleQuery queryParams ) {
        IPage<NormSampleVO> result = normSampleService.getNormSamplePage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "新增量常模样本定义")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('psy:norm-sample:add')")
    public Result<Void> saveNormSample(@RequestBody @Valid NormSampleForm formData ) {
        boolean result = normSampleService.saveNormSample(formData);
        return Result.judge(result);
    }

    @Operation(summary = "获取量常模样本定义表单数据")
    @GetMapping("/{id}/form")
    @PreAuthorize("@ss.hasPerm('psy:norm-sample:edit')")
    public Result<NormSampleForm> getNormSampleForm(
        @Parameter(description = "量常模样本定义ID") @PathVariable Long id
    ) {
        NormSampleForm formData = normSampleService.getNormSampleFormData(id);
        return Result.success(formData);
    }

    @Operation(summary = "修改量常模样本定义")
    @PutMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPerm('psy:norm-sample:edit')")
    public Result<Void> updateNormSample(
            @Parameter(description = "量常模样本定义ID") @PathVariable Long id,
            @RequestBody @Validated NormSampleForm formData
    ) {
        boolean result = normSampleService.updateNormSample(id, formData);
        return Result.judge(result);
    }

    @Operation(summary = "删除量常模样本定义")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('psy:norm-sample:delete')")
    public Result<Void> deleteNormSamples(
        @Parameter(description = "量常模样本定义ID，多个以英文逗号(,)分割") @PathVariable String ids
    ) {
        boolean result = normSampleService.deleteNormSamples(ids);
        return Result.judge(result);
    }
}
