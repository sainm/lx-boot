package com.lx.boot.psy.model.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 量表视图对象
 *
 * @author liuh
 * @since 2025-10-23 21:39
 */
@Getter
@Setter
@Schema( description = "量表视图对象")
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
}
