package com.lx.boot.psy.service.impl;

import com.lx.boot.common.exception.BusinessException;
import com.lx.boot.core.security.util.SecurityUtils;
import com.lx.boot.psy.model.entity.AssessmentAssignment;
import com.lx.boot.psy.model.entity.UserAnswer;
import com.lx.boot.psy.service.AssessmentAssignmentService;
import com.lx.boot.psy.service.QuestionService;
import com.lx.boot.psy.service.UserAnswerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.boot.psy.mapper.AssessmentRecordMapper;
import com.lx.boot.psy.service.AssessmentRecordService;
import com.lx.boot.psy.model.entity.AssessmentRecord;
import com.lx.boot.psy.model.form.AssessmentRecordForm;
import com.lx.boot.psy.model.query.AssessmentRecordQuery;
import com.lx.boot.psy.model.vo.AssessmentRecordVO;
import com.lx.boot.psy.converter.AssessmentRecordConverter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import org.springframework.transaction.annotation.Transactional;

/**
 * 测评记录服务实现类
 *
 * @author liuh
 * @since 2025-10-19 18:06
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssessmentRecordServiceImpl extends ServiceImpl<AssessmentRecordMapper, AssessmentRecord> implements AssessmentRecordService {

    private final AssessmentRecordConverter assessmentRecordConverter;
    private final UserAnswerService userAnswerService;
    private final QuestionService questionService;
    private final AssessmentAssignmentService assignmentService;

    /**
     * 获取测评记录分页列表
     *
     * @param queryParams 查询参数
     * @return {@link IPage<AssessmentRecordVO>} 测评记录分页列表
     */
    @Override
    public IPage<AssessmentRecordVO> getAssessmentRecordPage(AssessmentRecordQuery queryParams) {
        Page<AssessmentRecordVO> pageVO = this.baseMapper.getAssessmentRecordPage(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams
        );
        return pageVO;
    }

    /**
     * 获取测评记录表单数据
     *
     * @param id 测评记录ID
     * @return 测评记录表单数据
     */
    @Override
    public AssessmentRecordForm getAssessmentRecordFormData(Long id) {
        AssessmentRecord entity = this.getById(id);
        return assessmentRecordConverter.toForm(entity);
    }

    /**
     * 新增测评记录
     *
     * @param formData 测评记录表单对象
     * @return 是否新增成功
     */
    @Override
    public boolean saveAssessmentRecord(AssessmentRecordForm formData) {
        AssessmentRecord entity = assessmentRecordConverter.toEntity(formData);
        return this.save(entity);
    }

    /**
     * 更新测评记录
     *
     * @param id       测评记录ID
     * @param formData 测评记录表单对象
     * @return 是否修改成功
     */
    @Override
    public boolean updateAssessmentRecord(Long id, AssessmentRecordForm formData) {
        AssessmentRecord entity = assessmentRecordConverter.toEntity(formData);
        return this.updateById(entity);
    }

    /**
     * 删除测评记录
     *
     * @param ids 测评记录ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    @Override
    @Transactional
    public boolean deleteAssessmentRecords(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的测评记录数据为空");
        // 逻辑删除
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .toList();
        return this.removeByIds(idList);
    }

    /**
     * 提交测评
     * 核心流程：
     * 1. 验证测评记录存在且未提交
     * 2. 查询所有答案
     * 3. 计算总分（根据选项分数）
     * 4. 计算完成率
     * 5. 计算答题时长
     * 6. 更新测评记录状态为"已完成"
     * 7. 更新任务分配状态和进度
     */
    @Override
    public void submitAssessment(Long recordId) {
        log.info("========== 开始提交测评 ==========");
        log.info("测评记录ID: {}", recordId);

        // 1. 查询测评记录
        AssessmentRecord record = this.getById(recordId);
        if (record == null) {
            log.error("测评记录不存在: recordId={}", recordId);
            throw new BusinessException("测评记录不存在");
        }

        // 2. 校验状态（防止重复提交）
        if (record.getStatus() == 2) {
            log.warn("该测评已提交，请勿重复提交: recordId={}", recordId);
            throw new BusinessException("该测评已提交，请勿重复提交");
        }

        // 3. 查询该记录的所有答案
        List<UserAnswer> answers = userAnswerService.getByRecordId(recordId);
        log.info("查询到 {} 条答案", answers.size());

        // 4. 计算总分
        BigDecimal totalScore = calculateTotalScore(answers);
        log.info("总分: {}", totalScore);

        // 5. 计算完成率
        Integer totalQuestions = questionService.countByVersionId(record.getVersionId());
        int answeredQuestions = answers.size();
        Double completionRate = totalQuestions > 0
                ? (answeredQuestions * 100.0 / totalQuestions)
                : 0.0;
        log.info("完成率: {}% ({}/{})", completionRate, answeredQuestions, totalQuestions);

        // 6. 计算答题时长
        int durationSeconds = 0;
        if (record.getStartTime() != null) {
            durationSeconds = LocalDateTime.now().getSecond() - record.getStartTime().getSecond();

        }
        log.info("答题时长: {} 秒", durationSeconds);

        // 7. 更新测评记录
        record.setStatus(2); // 2-已完成
        record.setFinishTime(LocalDateTime.now());
        record.setTotalScore(totalScore);
        record.setCompletionRate(BigDecimal.valueOf(completionRate));
        record.setDurationSecond(durationSeconds);
        record.setUpdateTime(LocalDateTime.now());

        try {
            record.setUpdateBy(SecurityUtils.getUserId());
        } catch (Exception e) {
            log.warn("获取登录用户ID失败");
        }

        boolean updated = this.updateById(record);
        if (!updated) {
            log.error("更新测评记录失败: recordId={}", recordId);
            throw new BusinessException("更新测评记录失败");
        }

        log.info("✅ 测评记录更新成功");

        // 8. 更新任务分配状态
        if (record.getAssignmentId() != null) {
            updateAssignmentStatus(record.getAssignmentId(), completionRate);
        }

        log.info("========== 测评提交完成 ==========");
    }

    /**
     * 计算总分
     */
    private BigDecimal calculateTotalScore(List<UserAnswer> answers) {
        BigDecimal totalScore = BigDecimal.ZERO;

        for (UserAnswer answer : answers) {
            // 如果已经有分数，直接累加
            if (answer.getScore() != null && answer.getScore().compareTo(BigDecimal.ZERO) > 0) {
                totalScore = totalScore.add(answer.getScore());
                continue;
            }

            // 如果没有分数且有选项ID，查询选项分数
            if (answer.getOptionId() != null) {
                // 查询选项分数（需要 QuestionOptionService）
                BigDecimal optionScore = getOptionScore(answer.getOptionId());
                if (optionScore != null) {
                    totalScore = totalScore.add(optionScore);

                    // 更新答案的分数
                    answer.setScore(optionScore);
                    userAnswerService.updateById(answer);
                }
            }
        }

        return totalScore;
    }

    /**
     * 获取选项分数
     */
    private BigDecimal getOptionScore(Long optionId) {
        // 这里需要调用 QuestionOptionService 查询选项分数
        // 示例代码：
        // QuestionOption option = questionOptionService.getById(optionId);
        // return option != null ? option.getScore() : BigDecimal.ZERO;

        // 临时返回，实际需要查询数据库
        return BigDecimal.ZERO;
    }

    /**
     * 更新任务分配状态
     */
    private void updateAssignmentStatus(Long assignmentId, Double completionRate) {
        log.info("更新任务分配状态: assignmentId={}", assignmentId);

        AssessmentAssignment assignment = assignmentService.getAssessmentById(assignmentId);
        if (assignment != null) {
            assignment.setStatus(2); // 2-已完成
            assignment.setProgress(BigDecimal.valueOf(completionRate.intValue())); // 进度100%
            assignment.setUpdateTime(LocalDateTime.now());

            try {
                assignment.setUpdateBy(SecurityUtils.getUserId());
            } catch (Exception e) {
                log.warn("获取登录用户ID失败");
            }

            boolean updated = assignmentService.updateAssById(assignment);
            if (updated) {
                log.info("✅ 任务分配状态更新成功");
            } else {
                log.warn("⚠️ 任务分配状态更新失败");
            }
        }
    }

}
