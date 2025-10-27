package com.lx.boot.psy.model.entity;

import com.lx.boot.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;

/**
 * 题目实体对象
 *
 * @author liuh
 * @since 2025-10-19 18:01
 */
@Getter
@Setter
@TableName("psy_question")
public class Question extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 所属量表ID
     */
    private Long scaleId;
    /**
     * 所属维度ID
     */
    private Long dimensionId;

    private Long versionId;
    /**
     * 题目内容
     */
    private String questionText;
    /**
     * 题目类型: single/multiple/likert
     */
    private String questionType;
    /**
     * 题目顺序
     */
    private Integer orderNo;
    /**
     * 创建人ID
     */
    private Long createBy;
    /**
     * 更新人ID
     */
    private Long updateBy;
    /**
     * 是否删除（0: 未删除, 1: 已删除）
     */
    private Integer isDeleted;
}
