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
 * 量常模样本定义表单对象
 *
 * @author liuh
 * @since 2025-10-28 12:27
 */
@Getter
@Setter
@Schema(description = "量常模样本定义表单对象")
public class NormSampleForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "常模样本ID")
    @NotNull(message = "常模样本ID不能为空")
    private Long id;

    @Schema(description = "所属量表版本ID")
    @NotNull(message = "所属量表版本ID不能为空")
    private Long versionId;

    @Schema(description = "常模名称（如：大学生样本、青少年女性样本）")
    @NotBlank(message = "常模名称（如：大学生样本、青少年女性样本）不能为空")
    @Size(max=200, message="常模名称（如：大学生样本、青少年女性样本）长度不能超过200个字符")
    private String sampleName;

    @Schema(description = "性别（male/female/all）")
    @Size(max=10, message="性别（male/female/all）长度不能超过10个字符")
    private String gender;

    @Schema(description = "最小年龄")
    private Integer ageMin;

    @Schema(description = "最大年龄")
    private Integer ageMax;

    @Schema(description = "地区（可选）")
    @Size(max=100, message="地区（可选）长度不能超过100个字符")
    private String region;

    @Schema(description = "样本数量")
    private Integer sampleSize;

    @Schema(description = "样本说明")
    @Size(max=500, message="样本说明长度不能超过500个字符")
    private String description;

    private Long createBy;

    private Long updateBy;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;


}
