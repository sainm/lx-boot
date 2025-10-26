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
 * 量版本表单对象
 *
 * @author liuh
 * @since 2025-10-19 18:02
 */
@Getter
@Setter
@Schema(description = "量版本表单对象")
public class ScaleVersionForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @Schema(description = "所属量表ID")
    @NotNull(message = "所属量表ID不能为空")
    private Long scaleId;

    @Schema(description = "版本名称，如v1.0")
    @Size(max=100, message="版本名称，如v1.0长度不能超过100个字符")
    private String versionName;

    @Size(max=255, message="长度不能超过255个字符")
    private String description;

    @Schema(description = "是否启用")
    private Integer isActive;

    @Schema(description = "创建人ID")
    private Long createBy;

    @Schema(description = "创建时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新人ID")
    private Long updateBy;

    @Schema(description = "更新时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @Schema(description = "是否删除（0: 未删除, 1: 已删除）")
    private Integer isDeleted;


}
