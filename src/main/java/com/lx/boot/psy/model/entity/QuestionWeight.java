package com.lx.boot.psy.model.entity;

import com.lx.boot.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/**
 * 计分规则题目权重实体对象
 *
 * @author liuh
 * @since 2025-10-28 11:35
 */
@Getter
@Setter
@TableName("psy_question_weight")
public class QuestionWeight extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 所属计分规则ID
     */
    private Long ruleId;
    /**
     * 题目ID
     */
    private Long questionId;
    /**
     * 题目权重
     */
    private BigDecimal weight;
    /**
     * 创建人ID
     */
    private Long createBy;
    /**
     * 更新人ID
     */
    private Long updateBy;
    /**
     * 是否删除（0: 未删除, 1: 已删除）
     */
    private Integer isDeleted;
}
