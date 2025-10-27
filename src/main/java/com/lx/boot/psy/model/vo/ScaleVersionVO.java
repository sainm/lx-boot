package com.lx.boot.psy.model.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 量版本视图对象
 *
 * @author liuh
 * @since 2025-10-19 18:02
 */
@Getter
@Setter
@Schema( description = "量版本视图对象")
public class ScaleVersionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    @Schema(description = "所属量表ID")
    private Long scaleId;

    private String scaleName;
    @Schema(description = "版本名称，如v1.0")
    private String versionName;
    private String description;
    @Schema(description = "是否启用")
    private Integer isActive;
    @Schema(description = "创建人ID")
    private Long createBy;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "更新人ID")
    private Long updateBy;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
    @Schema(description = "是否删除（0: 未删除, 1: 已删除）")
    private Integer isDeleted;
}
