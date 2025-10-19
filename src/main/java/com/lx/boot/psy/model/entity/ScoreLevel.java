package com.lx.boot.psy.model.entity;

import com.lx.boot.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/**
 * 分数区间对应等级描述实体对象
 *
 * @author liuh
 * @since 2025-10-19 17:58
 */
@Getter
@Setter
@TableName("psy_score_level")
public class ScoreLevel extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 所属计分规则ID
     */
    private Long ruleId;
    /**
     * 分数下限
     */
    private BigDecimal minScore;
    /**
     * 分数上限
     */
    private BigDecimal maxScore;
    /**
     * 等级名称，如低、中、高
     */
    private String levelName;
    /**
     * 等级说明
     */
    private String description;
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
