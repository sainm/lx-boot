package com.lx.boot.psy.controller;

import com.lx.boot.psy.model.entity.UserAnswer;
import com.lx.boot.psy.service.UserAnswerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lx.boot.psy.model.form.UserAnswerForm;
import com.lx.boot.psy.model.query.UserAnswerQuery;
import com.lx.boot.psy.model.vo.UserAnswerVO;
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

import java.util.List;

/**
 * 用户答题记录前端控制层
 *
 * @author liuh
 * @since 2025-10-24 14:41
 */
@Tag(name = "用户答题记录接口")
@RestController
@RequestMapping("/api/v1/user-answer")
@RequiredArgsConstructor
public class UserAnswerController  {

    private final UserAnswerService userAnswerService;

    @Operation(summary = "用户答题记录分页列表")
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('psy:user-answer:query')")
    public PageResult<UserAnswerVO> getUserAnswerPage(UserAnswerQuery queryParams ) {
        IPage<UserAnswerVO> result = userAnswerService.getUserAnswerPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "新增用户答题记录")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('psy:user-answer:add')")
    public Result<Void> saveUserAnswer(@RequestBody @Valid UserAnswerForm formData ) {
        boolean result = userAnswerService.saveUserAnswer(formData);
        return Result.judge(result);
    }

    @Operation(summary = "获取用户答题记录表单数据")
    @GetMapping("/{id}/form")
    @PreAuthorize("@ss.hasPerm('psy:user-answer:edit')")
    public Result<UserAnswerForm> getUserAnswerForm(
        @Parameter(description = "用户答题记录ID") @PathVariable Long id
    ) {
        UserAnswerForm formData = userAnswerService.getUserAnswerFormData(id);
        return Result.success(formData);
    }

    @Operation(summary = "修改用户答题记录")
    @PutMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPerm('psy:user-answer:edit')")
    public Result<Void> updateUserAnswer(
            @Parameter(description = "用户答题记录ID") @PathVariable Long id,
            @RequestBody @Validated UserAnswerForm formData
    ) {
        boolean result = userAnswerService.updateUserAnswer(id, formData);
        return Result.judge(result);
    }

    @Operation(summary = "删除用户答题记录")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('psy:user-answer:delete')")
    public Result<Void> deleteUserAnswers(
        @Parameter(description = "用户答题记录ID，多个以英文逗号(,)分割") @PathVariable String ids
    ) {
        boolean result = userAnswerService.deleteUserAnswers(ids);
        return Result.judge(result);
    }

    /**
     * 获取测评记录的所有答案
     */
    @Operation(summary = "获取测评记录的所有答案")
    @GetMapping("/record/{recordId}")
    public Result<List<UserAnswer>> getAnswersByRecord(
            @Parameter(description = "测评记录ID") @PathVariable Long recordId
    ) {
        List<UserAnswer> answers = userAnswerService.getByRecordId(recordId);
        return Result.success(answers);
    }

    /**
     * 保存或更新答案
     * 每答完一道题调用此接口保存答案
     */
    @Operation(summary = "保存或更新答案")
    @PostMapping("/save")
    public Result<Void> saveAnswer(@RequestBody @Validated UserAnswer userAnswer) {
        userAnswerService.saveOrUpdateAnswer(userAnswer);
        return Result.success();
    }
}
