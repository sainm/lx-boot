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
 * 题目选项视图对象
 *
 * @author liuh
 * @since 2025-10-19 17:59
 */
@Getter
@Setter
@Schema( description = "题目选项视图对象")
public class OptionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    @Schema(description = "所属题目ID")
    private Long questionId;
    @Schema(description = "选项内容")
    private String optionText;
    @Schema(description = "选项分值")
    private Integer optionValue;
    @Schema(description = "可自定义分数，支持权重计算")
    private BigDecimal score;
    @Schema(description = "创建人ID")
    private Long createBy;
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
    @Schema(description = "更新人ID")
    private Long updateBy;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
    @Schema(description = "是否删除（0: 未删除, 1: 已删除）")
    private Integer isDeleted;
}
