package com.lx.boot.psy.model.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * 分数区间对应等级描述视图对象
 *
 * @author liuh
 * @since 2025-10-19 17:58
 */
@Getter
@Setter
@Schema( description = "分数区间对应等级描述视图对象")
public class ScoreLevelVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    @Schema(description = "所属计分规则ID")
    private Long ruleId;
    @Schema(description = "分数下限")
    private BigDecimal minScore;
    @Schema(description = "分数上限")
    private BigDecimal maxScore;
    @Schema(description = "等级名称，如低、中、高")
    private String levelName;
    @Schema(description = "等级说明")
    private String description;
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
