package com.lx.boot.psy.model.entity;

import com.lx.boot.common.base.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 量常模样本定义实体对象
 *
 * @author liuh
 * @since 2025-10-28 12:27
 */
@Getter
@Setter
@TableName("psy_norm_sample")
public class NormSample extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 所属量表版本ID
     */
    private Long versionId;
    /**
     * 常模名称（如：大学生样本、青少年女性样本）
     */
    private String sampleName;
    /**
     * 性别（male/female/all）
     */
    private String gender;
    /**
     * 最小年龄
     */
    private Integer ageMin;
    /**
     * 最大年龄
     */
    private Integer ageMax;
    /**
     * 地区（可选）
     */
    private String region;
    /**
     * 样本数量
     */
    private Integer sampleSize;
    /**
     * 样本说明
     */
    private String description;
    private Long createBy;
    private Long updateBy;
}
