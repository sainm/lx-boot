package com.lx.boot.psy.model.entity;

import com.lx.boot.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

/**
 * 测评报告实体对象
 *
 * @author liuh
 * @since 2025-10-19 18:05
 */
@Getter
@Setter
@TableName("psy_report")
public class Report extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 测评记录ID
     */
    private Long recordId;
    /**
     * 用户ID
     */
    private Long userId;
    /**
     * 量表ID
     */
    private Long scaleId;
    /**
     * 总分
     */
    private BigDecimal totalScore;
    /**
     * 结果等级（如低、中、高风险）
     */
    private String resultLevel;

    /**
     * 系统建议或干预建议
     */
    private String suggestion;
    /**
     * 创建人
     */
    private Long createBy;
    /**
     * 最后修改人
     */
    private Long updateBy;
}
