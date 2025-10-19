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
 * 用户答题记录视图对象
 *
 * @author liuh
 * @since 2025-10-19 18:03
 */
@Getter
@Setter
@Schema( description = "用户答题记录视图对象")
public class UserAnswerVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "答题记录ID")
    private Long id;
    @Schema(description = "测评记录ID")
    private Long recordId;
    @Schema(description = "题目ID")
    private Long questionId;
    @Schema(description = "选项ID（如为选择题）")
    private Long optionId;
    @Schema(description = "主观题答案文本")
    private String answerText;
    @Schema(description = "得分（按题计算）")
    private BigDecimal score;
    @Schema(description = "作答耗时（秒）")
    private Integer timeSpent;
    @Schema(description = "创建人")
    private Long createBy;
    @Schema(description = "最后修改人")
    private Long updateBy;
    @Schema(description = "答题时间")
    private LocalDateTime createTime;
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
