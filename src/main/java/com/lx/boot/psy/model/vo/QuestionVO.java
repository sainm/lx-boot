package com.lx.boot.psy.model.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * 题目视图对象
 *
 * @author liuh
 * @since 2025-10-19 18:01
 */
@Getter
@Setter
@Schema( description = "题目视图对象")
public class QuestionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    @Schema(description = "所属量表ID")
    private Long scaleId;
    @Schema(description = "所属维度ID")
    private Long dimensionId;
    @Schema(description = "题目内容")
    private String questionText;
    @Schema(description = "题目类型: single/multiple/likert")
    private String questionType;
    @Schema(description = "题目顺序")
    private Integer orderNo;
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
