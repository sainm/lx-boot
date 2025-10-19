package com.lx.boot.psy.model.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 测评预警记录视图对象
 *
 * @author liuh
 * @since 2025-10-19 18:04
 */
@Getter
@Setter
@Schema( description = "测评预警记录视图对象")
public class WarningVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "预警ID")
    private Long id;
    @Schema(description = "用户ID")
    private Long userId;
    @Schema(description = "关联测评记录ID")
    private Long recordId;
    @Schema(description = "预警类型（如高危、异常、极端答题）")
    private String warningType;
    @Schema(description = "预警等级：1一般 2严重 3紧急")
    private Integer level;
    @Schema(description = "触发规则标识（如规则编号）")
    private String triggerRule;
    @Schema(description = "详细说明")
    private String description;
    @Schema(description = "是否已处理：0未处理 1已处理")
    private Integer isResolved;
    @Schema(description = "处理人ID")
    private Long handlerId;
    @Schema(description = "处理时间")
    private LocalDateTime handleTime;
    @Schema(description = "创建人")
    private Long createBy;
    @Schema(description = "最后修改人")
    private Long updateBy;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
