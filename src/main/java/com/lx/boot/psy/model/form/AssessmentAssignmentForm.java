package com.lx.boot.psy.model.form;

import java.io.Serial;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;

/**
 * 测评任务分配表单对象
 *
 * @author liuh
 * @since 2025-10-19 18:05
 */
@Getter
@Setter
@Schema(description = "测评任务分配表单对象")
public class AssessmentAssignmentForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "任务分配ID")
    @NotNull(message = "任务分配ID不能为空")
    private Long id;

    @Schema(description = "测评计划ID")
    @NotNull(message = "测评计划ID不能为空")
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

    @Schema(description = "最后修改人")
    private Long updateBy;

    @Schema(description = "创建时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;


}
