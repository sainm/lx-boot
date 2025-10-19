package com.lx.boot.psy.model.entity;

import com.lx.boot.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 测评预警记录实体对象
 *
 * @author liuh
 * @since 2025-10-19 18:04
 */
@Getter
@Setter
@TableName("psy_warning")
public class Warning extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;
    /**
     * 关联测评记录ID
     */
    private Long recordId;
    /**
     * 预警类型（如高危、异常、极端答题）
     */
    private String warningType;
    /**
     * 预警等级：1一般 2严重 3紧急
     */
    private Integer level;
    /**
     * 触发规则标识（如规则编号）
     */
    private String triggerRule;
    /**
     * 详细说明
     */
    private String description;
    /**
     * 是否已处理：0未处理 1已处理
     */
    private Integer isResolved;
    /**
     * 处理人ID
     */
    private Long handlerId;
    /**
     * 处理时间
     */
    private LocalDateTime handleTime;
    /**
     * 创建人
     */
    private Long createBy;
    /**
     * 最后修改人
     */
    private Long updateBy;
}
