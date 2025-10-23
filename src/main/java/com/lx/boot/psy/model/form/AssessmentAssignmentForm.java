package com.lx.boot.psy.model.form;

import java.io.Serial;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

/**
 * 测评任务分配表单对象
 *
 * @author liuh
 * @since 2025-10-23 22:34
 */
@Getter
@Setter
@Schema(description = "测评任务分配表单对象")
public class AssessmentAssignmentForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "任务分配ID")
    private Long id;

    @Schema(description = "测评计划ID")
    private Long planId;

    @Schema(description = "用户ID（单人分配）")
    private Long userId;

    @Schema(description = "用户组ID（批量分配）")
    private Long groupId;

    @Schema(description = "分配类型：0个人 1组")
    private Integer assignType;

    @Schema(description = "答题进度百分比")
    private BigDecimal progress;

    @Schema(description = "状态：0未开始 1进行中 2已完成 3已过期")
    private Integer status;

    @Schema(description = "分配人ID")
    private Long assignedBy;

    @Schema(description = "创建人")
    private Long createBy;


}
