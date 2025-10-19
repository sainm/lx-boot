package com.lx.boot.psy.model.query;

import com.lx.boot.common.base.BasePageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 计分规则主（维度）分页查询对象
 *
 * @author liuh
 * @since 2025-10-19 17:59
 */
@Schema(description ="计分规则主（维度）查询对象")
@Getter
@Setter
public class ScoringRuleQuery extends BasePageQuery {

}
