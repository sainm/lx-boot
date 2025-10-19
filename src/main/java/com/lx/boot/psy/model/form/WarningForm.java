package com.lx.boot.psy.model.form;

import java.io.Serial;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;

/**
 * 测评预警记录表单对象
 *
 * @author liuh
 * @since 2025-10-19 18:04
 */
@Getter
@Setter
@Schema(description = "测评预警记录表单对象")
public class WarningForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "预警ID")
    @NotNull(message = "预警ID不能为空")
    private Long id;

    @Schema(description = "用户ID")
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Schema(description = "关联测评记录ID")
    private Long recordId;

    @Schema(description = "预警类型（如高危、异常、极端答题）")
    @NotBlank(message = "预警类型（如高危、异常、极端答题）不能为空")
    @Size(max=100, message="预警类型（如高危、异常、极端答题）长度不能超过100个字符")
    private String warningType;

    @Schema(description = "预警等级：1一般 2严重 3紧急")
    private Integer level;

    @Schema(description = "触发规则标识（如规则编号）")
    @Size(max=255, message="触发规则标识（如规则编号）长度不能超过255个字符")
    private String triggerRule;

    @Schema(description = "详细说明")
    @Size(max=500, message="详细说明长度不能超过500个字符")
    private String description;

    @Schema(description = "是否已处理：0未处理 1已处理")
    private Integer isResolved;

    @Schema(description = "处理人ID")
    private Long handlerId;

    @Schema(description = "处理时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handleTime;

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
