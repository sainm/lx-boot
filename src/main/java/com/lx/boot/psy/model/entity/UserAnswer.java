package com.lx.boot.psy.model.entity;

import com.lx.boot.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/**
 * 用户答题记录实体对象
 *
 * @author liuh
 * @since 2025-10-19 18:03
 */
@Getter
@Setter
@TableName("psy_user_answer")
public class UserAnswer extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 测评记录ID
     */
    private Long recordId;
    /**
     * 题目ID
     */
    private Long questionId;
    /**
     * 选项ID（如为选择题）
     */
    private Long optionId;
    /**
     * 主观题答案文本
     */
    private String answerText;
    /**
     * 得分（按题计算）
     */
    private BigDecimal score;
    /**
     * 作答耗时（秒）
     */
    private Integer timeSpent;
    /**
     * 创建人
     */
    private Long createBy;
    /**
     * 最后修改人
     */
    private Long updateBy;
}
