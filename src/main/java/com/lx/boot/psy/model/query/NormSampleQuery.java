package com.lx.boot.psy.model.query;

import com.lx.boot.common.base.BasePageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 量常模样本定义分页查询对象
 *
 * @author liuh
 * @since 2025-10-28 12:27
 */
@Schema(description ="量常模样本定义查询对象")
@Getter
@Setter
public class NormSampleQuery extends BasePageQuery {

}
