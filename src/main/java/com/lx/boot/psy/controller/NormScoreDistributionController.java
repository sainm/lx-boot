package com.lx.boot.psy.controller;

import com.lx.boot.psy.service.NormScoreDistributionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lx.boot.psy.model.form.NormScoreDistributionForm;
import com.lx.boot.psy.model.query.NormScoreDistributionQuery;
import com.lx.boot.psy.model.vo.NormScoreDistributionVO;
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
 * 常模分数分布前端控制层
 *
 * @author liuh
 * @since 2025-10-28 13:04
 */
@Tag(name = "常模分数分布接口")
@RestController
@RequestMapping("/api/v1/norm-score-distribution")
@RequiredArgsConstructor
public class NormScoreDistributionController  {

    private final NormScoreDistributionService normScoreDistributionService;

    @Operation(summary = "常模分数分布分页列表")
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('psy:norm-score-distribution:query')")
    public PageResult<NormScoreDistributionVO> getNormScoreDistributionPage(NormScoreDistributionQuery queryParams ) {
        IPage<NormScoreDistributionVO> result = normScoreDistributionService.getNormScoreDistributionPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "新增常模分数分布")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('psy:norm-score-distribution:add')")
    public Result<Void> saveNormScoreDistribution(@RequestBody @Valid NormScoreDistributionForm formData ) {
        boolean result = normScoreDistributionService.saveNormScoreDistribution(formData);
        return Result.judge(result);
    }

    @Operation(summary = "获取常模分数分布表单数据")
    @GetMapping("/{id}/form")
    @PreAuthorize("@ss.hasPerm('psy:norm-score-distribution:edit')")
    public Result<NormScoreDistributionForm> getNormScoreDistributionForm(
        @Parameter(description = "常模分数分布ID") @PathVariable Long id
    ) {
        NormScoreDistributionForm formData = normScoreDistributionService.getNormScoreDistributionFormData(id);
        return Result.success(formData);
    }

    @Operation(summary = "修改常模分数分布")
    @PutMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPerm('psy:norm-score-distribution:edit')")
    public Result<Void> updateNormScoreDistribution(
            @Parameter(description = "常模分数分布ID") @PathVariable Long id,
            @RequestBody @Validated NormScoreDistributionForm formData
    ) {
        boolean result = normScoreDistributionService.updateNormScoreDistribution(id, formData);
        return Result.judge(result);
    }

    @Operation(summary = "删除常模分数分布")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('psy:norm-score-distribution:delete')")
    public Result<Void> deleteNormScoreDistributions(
        @Parameter(description = "常模分数分布ID，多个以英文逗号(,)分割") @PathVariable String ids
    ) {
        boolean result = normScoreDistributionService.deleteNormScoreDistributions(ids);
        return Result.judge(result);
    }
}
