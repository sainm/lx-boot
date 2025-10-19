package com.lx.boot.psy.converter;

import org.mapstruct.Mapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.entity.ScoreLevel;
import com.lx.boot.psy.model.form.ScoreLevelForm;

/**
 * 分数区间对应等级描述对象转换器
 *
 * @author liuh
 * @since 2025-10-19 17:58
 */
@Mapper(componentModel = "spring")
public interface ScoreLevelConverter{

    ScoreLevelForm toForm(ScoreLevel entity);

    ScoreLevel toEntity(ScoreLevelForm formData);
}