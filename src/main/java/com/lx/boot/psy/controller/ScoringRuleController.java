package com.lx.boot.psy.controller;

import com.lx.boot.psy.service.ScoringRuleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lx.boot.psy.model.form.ScoringRuleForm;
import com.lx.boot.psy.model.query.ScoringRuleQuery;
import com.lx.boot.psy.model.vo.ScoringRuleVO;
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
 * 计分规则主（维度）前端控制层
 *
 * @author liuh
 * @since 2025-10-19 17:59
 */
@Tag(name = "计分规则主（维度）接口")
@RestController
@RequestMapping("/api/v1/scoring-rule")
@RequiredArgsConstructor
public class ScoringRuleController  {

    private final ScoringRuleService scoringRuleService;

    @Operation(summary = "计分规则主（维度）分页列表")
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('psy:scoring-rule:query')")
    public PageResult<ScoringRuleVO> getScoringRulePage(ScoringRuleQuery queryParams ) {
        IPage<ScoringRuleVO> result = scoringRuleService.getScoringRulePage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "新增计分规则主（维度）")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('psy:scoring-rule:add')")
    public Result<Void> saveScoringRule(@RequestBody @Valid ScoringRuleForm formData ) {
        boolean result = scoringRuleService.saveScoringRule(formData);
        return Result.judge(result);
    }

    @Operation(summary = "获取计分规则主（维度）表单数据")
    @GetMapping("/{id}/form")
    @PreAuthorize("@ss.hasPerm('psy:scoring-rule:edit')")
    public Result<ScoringRuleForm> getScoringRuleForm(
        @Parameter(description = "计分规则主（维度）ID") @PathVariable Long id
    ) {
        ScoringRuleForm formData = scoringRuleService.getScoringRuleFormData(id);
        return Result.success(formData);
    }

    @Operation(summary = "修改计分规则主（维度）")
    @PutMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPerm('psy:scoring-rule:edit')")
    public Result<Void> updateScoringRule(
            @Parameter(description = "计分规则主（维度）ID") @PathVariable Long id,
            @RequestBody @Validated ScoringRuleForm formData
    ) {
        boolean result = scoringRuleService.updateScoringRule(id, formData);
        return Result.judge(result);
    }

    @Operation(summary = "删除计分规则主（维度）")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('psy:scoring-rule:delete')")
    public Result<Void> deleteScoringRules(
        @Parameter(description = "计分规则主（维度）ID，多个以英文逗号(,)分割") @PathVariable String ids
    ) {
        boolean result = scoringRuleService.deleteScoringRules(ids);
        return Result.judge(result);
    }
}
