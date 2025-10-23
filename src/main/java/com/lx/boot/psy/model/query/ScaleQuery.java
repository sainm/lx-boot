package com.lx.boot.psy.model.query;

import com.lx.boot.common.base.BasePageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 量表分页查询对象
 *
 * @author liuh
 * @since 2025-10-23 21:39
 */
@Schema(description ="量表查询对象")
@Getter
@Setter
public class ScaleQuery extends BasePageQuery {

    @Schema(description = "量表名称")
    private String name;
    @Schema(description = "量表唯一编码")
    private String code;
    @Schema(description = "适用性别 M/F/ALL")
    private String applicableGender;
}
