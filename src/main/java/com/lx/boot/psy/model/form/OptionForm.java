package com.lx.boot.psy.model.form;

import java.io.Serial;
import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;

/**
 * 题目选项表单对象
 *
 * @author liuh
 * @since 2025-10-19 17:59
 */
@Getter
@Setter
@Schema(description = "题目选项表单对象")
public class OptionForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "不能为空")
    private Long id;

    @Schema(description = "所属题目ID")
    @NotNull(message = "所属题目ID不能为空")
    private Long questionId;

    @Schema(description = "选项内容")
    @Size(max=255, message="选项内容长度不能超过255个字符")
    private String optionText;

    @Schema(description = "选项分值")
    private Integer optionValue;

    @Schema(description = "可自定义分数，支持权重计算")
    private BigDecimal score;

    @Schema(description = "创建人ID")
    @NotNull(message = "创建人ID不能为空")
    private Long createBy;

    @Schema(description = "创建时间")
    @NotNull(message = "创建时间不能为空")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新人ID")
    private Long updateBy;

    @Schema(description = "更新时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @Schema(description = "是否删除（0: 未删除, 1: 已删除）")
    private Integer isDeleted;


}
