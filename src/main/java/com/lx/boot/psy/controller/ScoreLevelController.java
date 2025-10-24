package com.lx.boot.psy.controller;

import com.lx.boot.psy.service.ScoreLevelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lx.boot.psy.model.form.ScoreLevelForm;
import com.lx.boot.psy.model.query.ScoreLevelQuery;
import com.lx.boot.psy.model.vo.ScoreLevelVO;
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
 * 分数区间对应等级描述前端控制层
 *
 * @author liuh
 * @since 2025-10-24 14:01
 */
@Tag(name = "分数区间对应等级描述接口")
@RestController
@RequestMapping("/api/v1/score-level")
@RequiredArgsConstructor
public class ScoreLevelController  {

    private final ScoreLevelService scoreLevelService;

    @Operation(summary = "分数区间对应等级描述分页列表")
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('psy:score-level:query')")
    public PageResult<ScoreLevelVO> getScoreLevelPage(ScoreLevelQuery queryParams ) {
        IPage<ScoreLevelVO> result = scoreLevelService.getScoreLevelPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "新增分数区间对应等级描述")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('psy:score-level:add')")
    public Result<Void> saveScoreLevel(@RequestBody @Valid ScoreLevelForm formData ) {
        boolean result = scoreLevelService.saveScoreLevel(formData);
        return Result.judge(result);
    }

    @Operation(summary = "获取分数区间对应等级描述表单数据")
    @GetMapping("/{id}/form")
    @PreAuthorize("@ss.hasPerm('psy:score-level:edit')")
    public Result<ScoreLevelForm> getScoreLevelForm(
        @Parameter(description = "分数区间对应等级描述ID") @PathVariable Long id
    ) {
        ScoreLevelForm formData = scoreLevelService.getScoreLevelFormData(id);
        return Result.success(formData);
    }

    @Operation(summary = "修改分数区间对应等级描述")
    @PutMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPerm('psy:score-level:edit')")
    public Result<Void> updateScoreLevel(
            @Parameter(description = "分数区间对应等级描述ID") @PathVariable Long id,
            @RequestBody @Validated ScoreLevelForm formData
    ) {
        boolean result = scoreLevelService.updateScoreLevel(id, formData);
        return Result.judge(result);
    }

    @Operation(summary = "删除分数区间对应等级描述")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('psy:score-level:delete')")
    public Result<Void> deleteScoreLevels(
        @Parameter(description = "分数区间对应等级描述ID，多个以英文逗号(,)分割") @PathVariable String ids
    ) {
        boolean result = scoreLevelService.deleteScoreLevels(ids);
        return Result.judge(result);
    }
}
