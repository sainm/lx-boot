package com.lx.boot.psy.model.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 测评计划视图对象
 *
 * @author liuh
 * @since 2025-10-19 18:03
 */
@Getter
@Setter
@Schema( description = "测评计划视图对象")
public class AssessmentPlanVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "测评计划ID")
    private Long id;
    @Schema(description = "测评计划名称")
    private String name;
    @Schema(description = "测评计划说明")
    private String description;
    @Schema(description = "量表ID")
    private Long scaleId;
    private String scaleName;
    @Schema(description = "量表版本ID")
    private Long versionId;
    private String versionName;
    @Schema(description = "目标群体（标签或分组描述）")
    private String targetGroup;
    @Schema(description = "开始时间")
    private LocalDateTime startTime;
    @Schema(description = "结束时间")
    private LocalDateTime endTime;
    @Schema(description = "创建人ID")
    private Long creatorId;
    @Schema(description = "状态：1启用 0停用")
    private Integer status;
    @Schema(description = "创建人")
    private Long createBy;
    @Schema(description = "最后修改人")
    private Long updateBy;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
