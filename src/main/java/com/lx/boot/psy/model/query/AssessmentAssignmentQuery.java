package com.lx.boot.psy.model.query;

import com.lx.boot.common.base.BasePageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

/**
 * 测评任务分配分页查询对象
 *
 * @author liuh
 * @since 2025-10-19 18:05
 */
@Schema(description ="测评任务分配查询对象")
@Getter
@Setter
public class AssessmentAssignmentQuery extends BasePageQuery {

}
