package com.lx.boot.psy.model.entity;

import com.lx.boot.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/**
 * 测评任务分配实体对象
 *
 * @author liuh
 * @since 2025-10-19 18:05
 */
@Getter
@Setter
@TableName("psy_assessment_assignment")
public class AssessmentAssignment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 测评计划ID
     */
    private Long planId;
    /**
     * 用户ID（单人分配）
     */
    private Long userId;
    /**
     * 用户组ID（批量分配）
     */
    private Long groupId;
    /**
     * 分配类型：0个人 1组
     */
    private Integer assignType;
    /**
     * 答题进度百分比
     */
    private BigDecimal progress;
    /**
     * 状态：0未开始 1进行中 2已完成 3已过期
     */
    private Integer status;
    /**
     * 分配人ID
     */
    private Long assignedBy;
    /**
     * 创建人
     */
    private Long createBy;
    /**
     * 最后修改人
     */
    private Long updateBy;
}
