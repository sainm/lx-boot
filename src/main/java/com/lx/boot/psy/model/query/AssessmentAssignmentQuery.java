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
 * @since 2025-10-23 22:34
 */
@Schema(description ="测评任务分配查询对象")
@Getter
@Setter
public class AssessmentAssignmentQuery extends BasePageQuery {

    @Schema(description = "用户ID（单人分配）")
    private Long userId;
    @Schema(description = "用户组ID（批量分配）")
    private Long groupId;
    @Schema(description = "分配类型：0个人 1组")
    private Integer assignType;
    @Schema(description = "状态：0未开始 1进行中 2已完成 3已过期")
    private Integer status;
}
