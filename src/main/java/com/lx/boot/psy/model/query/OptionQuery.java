package com.lx.boot.psy.model.query;

import com.lx.boot.common.base.BasePageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

/**
 * 题目选项分页查询对象
 *
 * @author liuh
 * @since 2025-10-19 17:59
 */
@Schema(description ="题目选项查询对象")
@Getter
@Setter
public class OptionQuery extends BasePageQuery {

}
