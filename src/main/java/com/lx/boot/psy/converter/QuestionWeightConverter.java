package com.lx.boot.psy.converter;

import org.mapstruct.Mapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.entity.QuestionWeight;
import com.lx.boot.psy.model.form.QuestionWeightForm;

/**
 * 计分规则题目权重对象转换器
 *
 * @author liuh
 * @since 2025-10-28 11:35
 */
@Mapper(componentModel = "spring")
public interface QuestionWeightConverter{

    QuestionWeightForm toForm(QuestionWeight entity);

    QuestionWeight toEntity(QuestionWeightForm formData);
}