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
 * 常模分数分布视图对象
 *
 * @author liuh
 * @since 2025-10-28 13:04
 */
@Getter
@Setter
@Schema( description = "常模分数分布视图对象")
public class NormScoreDistributionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "常模分布ID")
    private Long id;
    @Schema(description = "常模样本ID")
    private Long sampleId;
    @Schema(description = "维度ID（如有分维度常模）")
    private Long dimensionId;
    @Schema(description = "原始分")
    private BigDecimal rawScore;
    @Schema(description = "百分位（0~100）")
    private BigDecimal percentile;
    @Schema(description = "T分（均值50，标准差10）")
    private BigDecimal tScore;
    @Schema(description = "Z分（均值0，标准差1）")
    private BigDecimal zScore;
    @Schema(description = "九分位（1~9）")
    private BigDecimal stanine;
    @Schema(description = "备注（如转换公式或来源）")
    private String description;
}
