package com.lx.boot.psy.controller;

import com.lx.boot.core.security.util.SecurityUtils;
import com.lx.boot.psy.model.entity.AssessmentAssignment;
import com.lx.boot.psy.model.entity.AssessmentRecord;
import com.lx.boot.psy.service.AssessmentAssignmentService;
import com.lx.boot.psy.service.AssessmentRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.lx.boot.psy.model.form.AssessmentRecordForm;
import com.lx.boot.psy.model.query.AssessmentRecordQuery;
import com.lx.boot.psy.model.vo.AssessmentRecordVO;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

/**
 * 测评记录前端控制层
 *
 * @author liuh
 * @since 2025-10-24 14:44
 */
@Tag(name = "测评记录接口")
@RestController
@RequestMapping("/api/v1/assessment-record")
@RequiredArgsConstructor
@Slf4j
public class AssessmentRecordController {

    private final AssessmentRecordService assessmentRecordService;

    private final AssessmentAssignmentService assessmentAssignmentService;

    @Operation(summary = "测评记录分页列表")
    @GetMapping("/page")
    @PreAuthorize("@ss.hasPerm('psy:assessment-record:query')")
    public PageResult<AssessmentRecordVO> getAssessmentRecordPage(AssessmentRecordQuery queryParams) {
        IPage<AssessmentRecordVO> result = assessmentRecordService.getAssessmentRecordPage(queryParams);
        return PageResult.success(result);
    }

    @Operation(summary = "新增测评记录")
    @PostMapping
    @PreAuthorize("@ss.hasPerm('psy:assessment-record:add')")
    public Result<Void> saveAssessmentRecord(@RequestBody @Valid AssessmentRecordForm formData) {
        boolean result = assessmentRecordService.saveAssessmentRecord(formData);
        return Result.judge(result);
    }

    @Operation(summary = "获取测评记录表单数据")
    @GetMapping("/{id}/form")
    @PreAuthorize("@ss.hasPerm('psy:assessment-record:edit')")
    public Result<AssessmentRecordForm> getAssessmentRecordForm(
            @Parameter(description = "测评记录ID") @PathVariable Long id
    ) {
        AssessmentRecordForm formData = assessmentRecordService.getAssessmentRecordFormData(id);
        return Result.success(formData);
    }

    //    @Operation(summary = "获取测评记录表单数据")
//    @PostMapping("/{id}/submit")
//    public Result<Boolean> submitAssessmentRecord(,@PathVariable Long id) {
//        AssessmentRecordForm formData = assessmentRecordService. (id);
//        return Result.success(true);
//    }
    @PostMapping("/get-or-create/{assignmentId}")
    public Result<AssessmentRecord> getOrCreateRecord(@PathVariable Long assignmentId) {
        // 1. 查询任务分配信息
        AssessmentAssignment assignment = assessmentAssignmentService.getAssessmentById(assignmentId);
        if (assignment == null) {
            return Result.failed("任务分配不存在");
        }

        // 2. 查询是否已有测评记录（未完成的）
        AssessmentRecord existingRecord = assessmentRecordService.lambdaQuery()
                .eq(AssessmentRecord::getAssignmentId, assignmentId)
                .eq(AssessmentRecord::getUserId, SecurityUtils.getUserId())
                .in(AssessmentRecord::getStatus, Arrays.asList(0, 1)) // 0未开始 1进行中
                .orderByDesc(AssessmentRecord::getCreateTime)
                .last("LIMIT 1")
                .one();

        // 3. 如果已有记录，直接返回
        if (existingRecord != null) {
            log.info("用户继续答题，recordId: {}", existingRecord.getId());
            return Result.success(existingRecord);
        }

        // 4. 创建新测评记录
        AssessmentRecord newRecord = new AssessmentRecord();
        newRecord.setAssignmentId(assignmentId);
        newRecord.setUserId(SecurityUtils.getUserId());
        newRecord.setScaleId(assignment.getScaleId());
        newRecord.setVersionId(assignment.getVersionId());
        newRecord.setStartTime(LocalDateTime.now());
        newRecord.setStatus(1); // 1-进行中
        newRecord.setTotalScore(BigDecimal.ZERO);
        newRecord.setCompletionRate(BigDecimal.valueOf(0.0));

        assessmentRecordService.save(newRecord);

        // 5. 更新任务分配状态
        if (assignment.getStatus() == 0) {
            assignment.setStatus(1); // 进行中
            assessmentAssignmentService.updateStatusById(assignment);
        }

        log.info("创建新测评记录，recordId: {}", newRecord.getId());
        return Result.success(newRecord);
    }

    @Operation(summary = "修改测评记录")
    @PutMapping(value = "/{id}")
    @PreAuthorize("@ss.hasPerm('psy:assessment-record:edit')")
    public Result<Void> updateAssessmentRecord(
            @Parameter(description = "测评记录ID") @PathVariable Long id,
            @RequestBody @Validated AssessmentRecordForm formData
    ) {
        boolean result = assessmentRecordService.updateAssessmentRecord(id, formData);
        return Result.judge(result);
    }

    @Operation(summary = "删除测评记录")
    @DeleteMapping("/{ids}")
    @PreAuthorize("@ss.hasPerm('psy:assessment-record:delete')")
    public Result<Void> deleteAssessmentRecords(
            @Parameter(description = "测评记录ID，多个以英文逗号(,)分割") @PathVariable String ids
    ) {
        boolean result = assessmentRecordService.deleteAssessmentRecords(ids);
        return Result.judge(result);
    }

    /**
     * 提交测评（完成答题）
     */
    @Operation(summary = "提交测评")
    @PostMapping("/{recordId}/submit")
    public Result<Void> submitAssessment(@Parameter(description = "测评记录ID") @PathVariable Long recordId) {
        assessmentRecordService.submitAssessment(recordId);
        return Result.success();
    }
}
