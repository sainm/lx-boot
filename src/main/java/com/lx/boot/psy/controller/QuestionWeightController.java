package com.lx.boot.psy.controller;

import com.lx.boot.psy.service.QuestionWeightService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lx.boot.psy.model.form.QuestionWeightForm;
import com.lx.boot.psy.model.query.QuestionWeightQuery;
import com.lx.boot.psy.model.vo.QuestionWeightVO;
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
 * 计分规则题目权重前端控制层
 *
 * @author liuh
 * @since 2025-10-19 17:55
 */
@Tag(name = "计分规则题目权重接口")
@RestController
@RequestMapping("/api/v1/question-weight")
@RequiredArgsConstructor
public class QuestionWeightController  {

    private final QuestionWeightService questionWeightService;

    @Operation(summary = "计分规则题目权重分页列表")
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('psy:question-weight:query')")
    public PageResult<QuestionWeightVO> getQuestionWeightPage(QuestionWeightQuery queryParams ) {
        IPage<QuestionWeightVO> result = questionWeightService.getQuestionWeightPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "新增计分规则题目权重")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('psy:question-weight:add')")
    public Result<Void> saveQuestionWeight(@RequestBody @Valid QuestionWeightForm formData ) {
        boolean result = questionWeightService.saveQuestionWeight(formData);
        return Result.judge(result);
    }

    @Operation(summary = "获取计分规则题目权重表单数据")
    @GetMapping("/{id}/form")
    @PreAuthorize("@ss.hasPerm('psy:question-weight:edit')")
    public Result<QuestionWeightForm> getQuestionWeightForm(
        @Parameter(description = "计分规则题目权重ID") @PathVariable Long id
    ) {
        QuestionWeightForm formData = questionWeightService.getQuestionWeightFormData(id);
        return Result.success(formData);
    }

    @Operation(summary = "修改计分规则题目权重")
    @PutMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPerm('psy:question-weight:edit')")
    public Result<Void> updateQuestionWeight(
            @Parameter(description = "计分规则题目权重ID") @PathVariable Long id,
            @RequestBody @Validated QuestionWeightForm formData
    ) {
        boolean result = questionWeightService.updateQuestionWeight(id, formData);
        return Result.judge(result);
    }

    @Operation(summary = "删除计分规则题目权重")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('psy:question-weight:delete')")
    public Result<Void> deleteQuestionWeights(
        @Parameter(description = "计分规则题目权重ID，多个以英文逗号(,)分割") @PathVariable String ids
    ) {
        boolean result = questionWeightService.deleteQuestionWeights(ids);
        return Result.judge(result);
    }
}
