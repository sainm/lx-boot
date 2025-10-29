package com.lx.boot.psy.service.impl;

import com.lx.boot.core.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.boot.psy.mapper.UserAnswerMapper;
import com.lx.boot.psy.service.UserAnswerService;
import com.lx.boot.psy.model.entity.UserAnswer;
import com.lx.boot.psy.model.form.UserAnswerForm;
import com.lx.boot.psy.model.query.UserAnswerQuery;
import com.lx.boot.psy.model.vo.UserAnswerVO;
import com.lx.boot.psy.converter.UserAnswerConverter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户答题记录服务实现类
 *
 * @author liuh
 * @since 2025-10-19 18:03
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserAnswerServiceImpl extends ServiceImpl<UserAnswerMapper, UserAnswer> implements UserAnswerService {

    private final UserAnswerConverter userAnswerConverter;

    /**
     * 获取用户答题记录分页列表
     *
     * @param queryParams 查询参数
     * @return {@link IPage<UserAnswerVO>} 用户答题记录分页列表
     */
    @Override
    public IPage<UserAnswerVO> getUserAnswerPage(UserAnswerQuery queryParams) {
        Page<UserAnswerVO> pageVO = this.baseMapper.getUserAnswerPage(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams
        );
        return pageVO;
    }

    /**
     * 获取用户答题记录表单数据
     *
     * @param id 用户答题记录ID
     * @return 用户答题记录表单数据
     */
    @Override
    public UserAnswerForm getUserAnswerFormData(Long id) {
        UserAnswer entity = this.getById(id);
        return userAnswerConverter.toForm(entity);
    }

    /**
     * 新增用户答题记录
     *
     * @param formData 用户答题记录表单对象
     * @return 是否新增成功
     */
    @Override
    public boolean saveUserAnswer(UserAnswerForm formData) {
        UserAnswer entity = userAnswerConverter.toEntity(formData);
        return this.save(entity);
    }

    /**
     * 更新用户答题记录
     *
     * @param id       用户答题记录ID
     * @param formData 用户答题记录表单对象
     * @return 是否修改成功
     */
    @Override
    public boolean updateUserAnswer(Long id, UserAnswerForm formData) {
        UserAnswer entity = userAnswerConverter.toEntity(formData);
        return this.updateById(entity);
    }

    /**
     * 删除用户答题记录
     *
     * @param ids 用户答题记录ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    @Override
    @Transactional
    public boolean deleteUserAnswers(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的用户答题记录数据为空");
        // 逻辑删除
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .toList();
        return this.removeByIds(idList);
    }

    @Override
    public List<UserAnswer> getByRecordId(Long recordId) {
        log.info("查询测评记录 {} 的所有答案", recordId);

        return this.lambdaQuery()
                .eq(UserAnswer::getRecordId, recordId)
                .orderByAsc(UserAnswer::getQuestionId)
                .list();
    }

    /**
     * 保存或更新答案
     * <p>
     * 逻辑：
     * 1. 根据 recordId + questionId 查询是否已有答案
     * 2. 如果已存在 → 更新答案
     * 3. 如果不存在 → 新增答案
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveOrUpdateAnswer(UserAnswer userAnswer) {
        log.info("保存答案: recordId={}, questionId={}, optionId={}",
                userAnswer.getRecordId(),
                userAnswer.getQuestionId(),
                userAnswer.getOptionId());

        // 1. 查询是否已存在该题目的答案
        UserAnswer existingAnswer = this.lambdaQuery()
                .eq(UserAnswer::getRecordId, userAnswer.getRecordId())
                .eq(UserAnswer::getQuestionId, userAnswer.getQuestionId())
                .one();

        if (existingAnswer != null) {
            // 2. 更新已有答案
            log.info("更新已有答案，answerId={}", existingAnswer.getId());

            // 更新字段
            existingAnswer.setOptionId(userAnswer.getOptionId());
            existingAnswer.setAnswerText(userAnswer.getAnswerText());
            existingAnswer.setTimeSpent(userAnswer.getTimeSpent());
            existingAnswer.setUpdateTime(LocalDateTime.now());

            // 设置修改人（从登录信息获取）
            try {
                existingAnswer.setUpdateBy(SecurityUtils.getUserId());
            } catch (Exception e) {
                log.warn("未登录或获取用户ID失败");
            }

            // 执行更新
            boolean updated = this.updateById(existingAnswer);

            if (updated) {
                log.info("✅ 答案更新成功");
            } else {
                log.error("❌ 答案更新失败");
                throw new RuntimeException("答案更新失败");
            }

        } else {
            // 3. 新增答案
            log.info("新增答案");

            // 设置创建时间和更新时间
            userAnswer.setCreateTime(LocalDateTime.now());
            userAnswer.setUpdateTime(LocalDateTime.now());

            // 设置创建人和修改人
            try {
                Long userId = SecurityUtils.getUserId();
                userAnswer.setCreateBy(userId);
                userAnswer.setUpdateBy(userId);
            } catch (Exception e) {
                log.warn("未登录或获取用户ID失败");
            }

            // 分数初始化为0，提交时再计算
            if (userAnswer.getScore() == null) {
                userAnswer.setScore(java.math.BigDecimal.ZERO);
            }

            // 执行保存
            boolean saved = this.save(userAnswer);

            if (saved) {
                log.info("✅ 答案保存成功，answerId={}", userAnswer.getId());
            } else {
                log.error("❌ 答案保存失败");
                throw new RuntimeException("答案保存失败");
            }
        }
    }
}