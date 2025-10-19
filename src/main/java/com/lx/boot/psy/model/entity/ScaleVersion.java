package com.lx.boot.psy.model.entity;

import com.lx.boot.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 量版本实体对象
 *
 * @author liuh
 * @since 2025-10-19 18:02
 */
@Getter
@Setter
@TableName("psy_scale_version")
public class ScaleVersion extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 所属量表ID
     */
    private Long scaleId;
    /**
     * 版本名称，如v1.0
     */
    private String versionName;
    private String description;
    /**
     * 是否启用
     */
    private Integer isActive;
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
