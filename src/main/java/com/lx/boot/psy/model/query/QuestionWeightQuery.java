package com.lx.boot.psy.model.query;

import com.lx.boot.common.base.BasePageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

/**
 * 计分规则题目权重分页查询对象
 *
 * @author liuh
 * @since 2025-10-28 11:35
 */
@Schema(description ="计分规则题目权重查询对象")
@Getter
@Setter
public class QuestionWeightQuery extends BasePageQuery {

    @Schema(description = "所属计分规则ID")
    private Long ruleId;
    @Schema(description = "题目ID")
    private Long questionId;
}
