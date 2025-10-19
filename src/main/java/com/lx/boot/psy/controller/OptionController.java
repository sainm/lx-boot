package com.lx.boot.psy.controller;

import com.lx.boot.psy.service.OptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lx.boot.psy.model.form.OptionForm;
import com.lx.boot.psy.model.query.OptionQuery;
import com.lx.boot.psy.model.vo.OptionVO;
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
 * 题目选项前端控制层
 *
 * @author liuh
 * @since 2025-10-19 17:59
 */
@Tag(name = "题目选项接口")
@RestController
@RequestMapping("/api/v1/option")
@RequiredArgsConstructor
public class OptionController  {

    private final OptionService optionService;

    @Operation(summary = "题目选项分页列表")
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('psy:option:query')")
    public PageResult<OptionVO> getOptionPage(OptionQuery queryParams ) {
        IPage<OptionVO> result = optionService.getOptionPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "新增题目选项")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('psy:option:add')")
    public Result<Void> saveOption(@RequestBody @Valid OptionForm formData ) {
        boolean result = optionService.saveOption(formData);
        return Result.judge(result);
    }

    @Operation(summary = "获取题目选项表单数据")
    @GetMapping("/{id}/form")
    @PreAuthorize("@ss.hasPerm('psy:option:edit')")
    public Result<OptionForm> getOptionForm(
        @Parameter(description = "题目选项ID") @PathVariable Long id
    ) {
        OptionForm formData = optionService.getOptionFormData(id);
        return Result.success(formData);
    }

    @Operation(summary = "修改题目选项")
    @PutMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPerm('psy:option:edit')")
    public Result<Void> updateOption(
            @Parameter(description = "题目选项ID") @PathVariable Long id,
            @RequestBody @Validated OptionForm formData
    ) {
        boolean result = optionService.updateOption(id, formData);
        return Result.judge(result);
    }

    @Operation(summary = "删除题目选项")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('psy:option:delete')")
    public Result<Void> deleteOptions(
        @Parameter(description = "题目选项ID，多个以英文逗号(,)分割") @PathVariable String ids
    ) {
        boolean result = optionService.deleteOptions(ids);
        return Result.judge(result);
    }
}
