package com.lx.boot.psy.model.entity;

import com.lx.boot.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 测评计划实体对象
 *
 * @author liuh
 * @since 2025-10-19 18:03
 */
@Getter
@Setter
@TableName("psy_assessment_plan")
public class AssessmentPlan extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 测评计划名称
     */
    private String name;
    /**
     * 测评计划说明
     */
    private String description;
    /**
     * 量表ID
     */
    private Long scaleId;
    /**
     * 量表版本ID
     */
    private Long versionId;
    /**
     * 目标群体（标签或分组描述）
     */
    private String targetGroup;
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    /**
     * 结束时间
     */
    private LocalDateTime endTime;
    /**
     * 创建人ID
     */
    private Long creatorId;
    /**
     * 状态：1启用 0停用
     */
    private Integer status;
    /**
     * 创建人
     */
    private Long createBy;
    /**
     * 最后修改人
     */
    private Long updateBy;
}
