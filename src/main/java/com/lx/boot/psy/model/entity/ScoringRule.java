package com.lx.boot.psy.model.entity;

import com.lx.boot.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 计分规则主（维度）实体对象
 *
 * @author liuh
 * @since 2025-10-19 17:59
 */
@Getter
@Setter
@TableName("psy_scoring_rule")
public class ScoringRule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 所属维度ID
     */
    private Long dimensionId;
    /**
     * 计分说明
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
