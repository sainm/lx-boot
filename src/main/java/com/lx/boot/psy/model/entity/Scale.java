package com.lx.boot.psy.model.entity;

import com.lx.boot.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 量主实体对象
 *
 * @author liuh
 * @since 2025-10-19 18:02
 */
@Getter
@Setter
@TableName("psy_scale")
public class Scale extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 量表名称
     */
    private String name;
    /**
     * 量表唯一编码
     */
    private String code;
    /**
     * 量表说明
     */
    private String description;
    /**
     * 适用年龄段，如18-65
     */
    private String applicableAge;
    /**
     * 适用性别 M/F/ALL
     */
    private String applicableGender;
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
