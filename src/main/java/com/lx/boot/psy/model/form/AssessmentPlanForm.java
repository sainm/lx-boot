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
 * 测评计划表单对象
 *
 * @author liuh
 * @since 2025-10-19 18:03
 */
@Getter
@Setter
@Schema(description = "测评计划表单对象")
public class AssessmentPlanForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "测评计划ID")
    @NotNull(message = "测评计划ID不能为空")
    private Long id;

    @Schema(description = "测评计划名称")
    @NotBlank(message = "测评计划名称不能为空")
    @Size(max=200, message="测评计划名称长度不能超过200个字符")
    private String name;

    @Schema(description = "测评计划说明")
    @Size(max=1000, message="测评计划说明长度不能超过1000个字符")
    private String description;

    @Schema(description = "量表ID")
    @NotNull(message = "量表ID不能为空")
    private Long scaleId;

    @Schema(description = "量表版本ID")
    private Long versionId;

    @Schema(description = "目标群体（标签或分组描述）")
    @Size(max=255, message="目标群体（标签或分组描述）长度不能超过255个字符")
    private String targetGroup;

    @Schema(description = "开始时间")
    @NotNull(message = "开始时间不能为空")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @Schema(description = "创建人ID")
    @NotNull(message = "创建人ID不能为空")
    private Long creatorId;

    @Schema(description = "状态：1启用 0停用")
    private Integer status;

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
