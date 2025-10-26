package com.lx.boot.psy.model.query;

import com.lx.boot.common.base.BasePageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 量版本分页查询对象
 *
 * @author liuh
 * @since 2025-10-19 18:02
 */
@Schema(description ="量版本查询对象")
@Getter
@Setter
public class ScaleVersionQuery extends BasePageQuery {

    private Long scaleId;

}
