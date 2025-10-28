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
 * 计分规则题目权重视图对象
 *
 * @author liuh
 * @since 2025-10-28 11:35
 */
@Getter
@Setter
@Schema( description = "计分规则题目权重视图对象")
public class QuestionWeightVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    @Schema(description = "所属计分规则ID")
    private Long ruleId;
    @Schema(description = "题目ID")
    private Long questionId;
    @Schema(description = "题目权重")
    private BigDecimal weight;
}
