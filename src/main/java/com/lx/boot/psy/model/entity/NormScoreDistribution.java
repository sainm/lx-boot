package com.lx.boot.psy.model.entity;

import com.lx.boot.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/**
 * 常模分数分布实体对象
 *
 * @author liuh
 * @since 2025-10-28 13:04
 */
@Getter
@Setter
@TableName("psy_norm_score_distribution")
public class NormScoreDistribution extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 常模样本ID
     */
    private Long sampleId;
    /**
     * 维度ID（如有分维度常模）
     */
    private Long dimensionId;
    /**
     * 原始分
     */
    private BigDecimal rawScore;
    /**
     * 百分位（0~100）
     */
    private BigDecimal percentile;
    /**
     * T分（均值50，标准差10）
     */
    private BigDecimal tScore;
    /**
     * Z分（均值0，标准差1）
     */
    private BigDecimal zScore;
    /**
     * 九分位（1~9）
     */
    private BigDecimal stanine;
    /**
     * 备注（如转换公式或来源）
     */
    private String description;
    private Long createBy;
    private Long updateBy;
}
