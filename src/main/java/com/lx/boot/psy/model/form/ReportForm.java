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
 * 测评报告表单对象
 *
 * @author liuh
 * @since 2025-10-19 18:05
 */
@Getter
@Setter
@Schema(description = "测评报告表单对象")
public class ReportForm implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "报告ID")
    @NotNull(message = "报告ID不能为空")
    private Long id;

    @Schema(description = "测评记录ID")
    @NotNull(message = "测评记录ID不能为空")
    private Long recordId;

    @Schema(description = "用户ID")
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @Schema(description = "量表ID")
    @NotNull(message = "量表ID不能为空")
    private Long scaleId;

    @Schema(description = "总分")
    private BigDecimal totalScore;

    @Schema(description = "结果等级（如低、中、高风险）")
    @Size(max=100, message="结果等级（如低、中、高风险）长度不能超过100个字符")
    private String resultLevel;

    @Schema(description = "结果详情（维度分数、解释说明等）")
    @Size(max=-1, message="结果详情（维度分数、解释说明等）长度不能超过-1个字符")
    private String resultJson;

    @Schema(description = "系统建议或干预建议")
    @Size(max=65535, message="系统建议或干预建议长度不能超过65535个字符")
    private String suggestion;

    @Schema(description = "创建人")
    private Long createBy;

    @Schema(description = "最后修改人")
    private Long updateBy;

    @Schema(description = "生成时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;


}
