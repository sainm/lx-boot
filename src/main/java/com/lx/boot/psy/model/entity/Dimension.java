package com.lx.boot.psy.model.entity;

import com.lx.boot.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 维度实体对象
 *
 * @author liuh
 * @since 2025-10-19 18:01
 */
@Getter
@Setter
@TableName("psy_dimension")
public class Dimension extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 所属量表ID
     */
    private Long scaleId;

    /**
     * 版本ID
     */
    private Long versionId;
    /**
     * 维度名称，如焦虑、抑郁
     */
    private String name;

    private String description;
    /**
     * 计分规则，如sum/average
     */
    private String scoreRule;
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
