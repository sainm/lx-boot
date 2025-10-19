package com.lx.boot.psy.model.entity;

import com.lx.boot.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 测评记录实体对象
 *
 * @author liuh
 * @since 2025-10-19 18:06
 */
@Getter
@Setter
@TableName("psy_assessment_record")
public class AssessmentRecord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 任务分配ID
     */
    private Long assignmentId;
    /**
     * 用户ID
     */
    private Long userId;
    /**
     * 量表ID
     */
    private Long scaleId;
    /**
     * 量表版本ID
     */
    private Long versionId;
    /**
     * 答题开始时间
     */
    private LocalDateTime startTime;
    /**
     * 答题完成时间
     */
    private LocalDateTime finishTime;
    /**
     * 总得分
     */
    private BigDecimal totalScore;
    /**
     * 完成率
     */
    private BigDecimal completionRate;
    /**
     * 状态：0未完成 1已完成 2中断
     */
    private Integer status;
    /**
     * 作答时长（秒）
     */
    private Integer durationSecond;
    /**
     * 创建人
     */
    private Long createBy;
    /**
     * 最后修改人
     */
    private Long updateBy;
}
