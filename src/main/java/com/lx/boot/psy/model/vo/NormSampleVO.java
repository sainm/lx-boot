package com.lx.boot.psy.model.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 量常模样本定义视图对象
 *
 * @author liuh
 * @since 2025-10-28 12:27
 */
@Getter
@Setter
@Schema( description = "量常模样本定义视图对象")
public class NormSampleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "常模样本ID")
    private Long id;
    @Schema(description = "所属量表版本ID")
    private Long versionId;
    @Schema(description = "常模名称（如：大学生样本、青少年女性样本）")
    private String sampleName;
    @Schema(description = "性别（male/female/all）")
    private String gender;
    @Schema(description = "最小年龄")
    private Integer ageMin;
    @Schema(description = "最大年龄")
    private Integer ageMax;
    @Schema(description = "地区（可选）")
    private String region;
    @Schema(description = "样本数量")
    private Integer sampleSize;
    @Schema(description = "样本说明")
    private String description;
    private Long createBy;
    private Long updateBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
