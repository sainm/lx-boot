package com.lx.boot.psy.model.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 量主视图对象
 *
 * @author liuh
 * @since 2025-10-19 18:02
 */
@Getter
@Setter
@Schema( description = "量主视图对象")
public class ScaleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    @Schema(description = "量表名称")
    private String name;
    @Schema(description = "量表唯一编码")
    private String code;
    @Schema(description = "量表说明")
    private String description;
    @Schema(description = "适用年龄段，如18-65")
    private String applicableAge;
    @Schema(description = "适用性别 M/F/ALL")
    private String applicableGender;
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
