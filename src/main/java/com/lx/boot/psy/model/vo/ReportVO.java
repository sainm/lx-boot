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
 * 测评报告视图对象
 *
 * @author liuh
 * @since 2025-10-19 18:05
 */
@Getter
@Setter
@Schema( description = "测评报告视图对象")
public class ReportVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "报告ID")
    private Long id;
    @Schema(description = "测评记录ID")
    private Long recordId;
    @Schema(description = "用户ID")
    private Long userId;
    @Schema(description = "量表ID")
    private Long scaleId;
    @Schema(description = "总分")
    private BigDecimal totalScore;
    @Schema(description = "结果等级（如低、中、高风险）")
    private String resultLevel;
    @Schema(description = "结果详情（维度分数、解释说明等）")
    private String resultJson;
    @Schema(description = "系统建议或干预建议")
    private String suggestion;
    @Schema(description = "创建人")
    private Long createBy;
    @Schema(description = "最后修改人")
    private Long updateBy;
    @Schema(description = "生成时间")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
