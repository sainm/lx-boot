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
 * 量主表单对象
 *
 * @author liuh
 * @since 2025-10-19 18:02
 */
@Getter
@Setter
@Schema(description = "量主表单对象")
public class ScaleForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "不能为空")
    private Long id;

    @Schema(description = "量表名称")
    @NotBlank(message = "量表名称不能为空")
    @Size(max=200, message="量表名称长度不能超过200个字符")
    private String name;

    @Schema(description = "量表唯一编码")
    @Size(max=100, message="量表唯一编码长度不能超过100个字符")
    private String code;

    @Schema(description = "量表说明")
    @Size(max=500, message="量表说明长度不能超过500个字符")
    private String description;

    @Schema(description = "适用年龄段，如18-65")
    @Size(max=50, message="适用年龄段，如18-65长度不能超过50个字符")
    private String applicableAge;

    @Schema(description = "适用性别 M/F/ALL")
    @Size(max=10, message="适用性别 M/F/ALL长度不能超过10个字符")
    private String applicableGender;

    @Schema(description = "创建人ID")
    @NotNull(message = "创建人ID不能为空")
    private Long createBy;

    @Schema(description = "创建时间")
    @NotNull(message = "创建时间不能为空")
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
