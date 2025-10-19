package com.lx.boot.psy.model.form;

import java.io.Serial;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;

/**
 * 分数区间对应等级描述表单对象
 *
 * @author liuh
 * @since 2025-10-19 17:58
 */
@Getter
@Setter
@Schema(description = "分数区间对应等级描述表单对象")
public class ScoreLevelForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "不能为空")
    private Long id;

    @Schema(description = "所属计分规则ID")
    @NotNull(message = "所属计分规则ID不能为空")
    private Long ruleId;

    @Schema(description = "分数下限")
    private BigDecimal minScore;

    @Schema(description = "分数上限")
    private BigDecimal maxScore;

    @Schema(description = "等级名称，如低、中、高")
    @Size(max=100, message="等级名称，如低、中、高长度不能超过100个字符")
    private String levelName;

    @Schema(description = "等级说明")
    @Size(max=255, message="等级说明长度不能超过255个字符")
    private String description;

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
