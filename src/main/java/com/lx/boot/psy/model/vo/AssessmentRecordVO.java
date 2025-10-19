package com.lx.boot.psy.model.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * 测评记录视图对象
 *
 * @author liuh
 * @since 2025-10-19 18:06
 */
@Getter
@Setter
@Schema( description = "测评记录视图对象")
public class AssessmentRecordVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "测评记录ID")
    private Long id;
    @Schema(description = "任务分配ID")
    private Long assignmentId;
    @Schema(description = "用户ID")
    private Long userId;
    @Schema(description = "量表ID")
    private Long scaleId;
    @Schema(description = "量表版本ID")
    private Long versionId;
    @Schema(description = "答题开始时间")
    private LocalDateTime startTime;
    @Schema(description = "答题完成时间")
    private LocalDateTime finishTime;
    @Schema(description = "总得分")
    private BigDecimal totalScore;
    @Schema(description = "完成率")
    private BigDecimal completionRate;
    @Schema(description = "状态：0未完成 1已完成 2中断")
    private Integer status;
    @Schema(description = "作答时长（秒）")
    private Integer durationSecond;
    @Schema(description = "创建人")
    private Long createBy;
    @Schema(description = "最后修改人")
    private Long updateBy;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
