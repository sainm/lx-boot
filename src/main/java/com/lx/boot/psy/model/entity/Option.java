package com.lx.boot.psy.model.entity;

import com.lx.boot.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/**
 * 题目选项实体对象
 *
 * @author liuh
 * @since 2025-10-19 17:59
 */
@Getter
@Setter
@TableName("psy_option")
public class Option extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 所属题目ID
     */
    private Long questionId;
    /**
     * 选项内容
     */
    private String optionText;
    /**
     * 选项分值
     */
    private Integer optionValue;
    /**
     * 可自定义分数，支持权重计算
     */
    private BigDecimal score;
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
