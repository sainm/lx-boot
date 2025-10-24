package com.lx.boot.psy.controller;

import com.lx.boot.psy.service.QuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lx.boot.psy.model.form.QuestionForm;
import com.lx.boot.psy.model.query.QuestionQuery;
import com.lx.boot.psy.model.vo.QuestionVO;
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
 * 题目前端控制层
 *
 * @author liuh
 * @since 2025-10-24 13:41
 */
@Tag(name = "题目接口")
@RestController
@RequestMapping("/api/v1/question")
@RequiredArgsConstructor
public class QuestionController  {

    private final QuestionService questionService;

    @Operation(summary = "题目分页列表")
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('psy:question:query')")
    public PageResult<QuestionVO> getQuestionPage(QuestionQuery queryParams ) {
        IPage<QuestionVO> result = questionService.getQuestionPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "新增题目")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('psy:question:add')")
    public Result<Void> saveQuestion(@RequestBody @Valid QuestionForm formData ) {
        boolean result = questionService.saveQuestion(formData);
        return Result.judge(result);
    }

    @Operation(summary = "获取题目表单数据")
    @GetMapping("/{id}/form")
    @PreAuthorize("@ss.hasPerm('psy:question:edit')")
    public Result<QuestionForm> getQuestionForm(
        @Parameter(description = "题目ID") @PathVariable Long id
    ) {
        QuestionForm formData = questionService.getQuestionFormData(id);
        return Result.success(formData);
    }

    @Operation(summary = "修改题目")
    @PutMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPerm('psy:question:edit')")
    public Result<Void> updateQuestion(
            @Parameter(description = "题目ID") @PathVariable Long id,
            @RequestBody @Validated QuestionForm formData
    ) {
        boolean result = questionService.updateQuestion(id, formData);
        return Result.judge(result);
    }

    @Operation(summary = "删除题目")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('psy:question:delete')")
    public Result<Void> deleteQuestions(
        @Parameter(description = "题目ID，多个以英文逗号(,)分割") @PathVariable String ids
    ) {
        boolean result = questionService.deleteQuestions(ids);
        return Result.judge(result);
    }
}
