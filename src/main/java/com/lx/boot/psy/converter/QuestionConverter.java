package com.lx.boot.psy.converter;

import com.lx.boot.psy.model.vo.QuestionVO;
import org.mapstruct.Mapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.entity.Question;
import com.lx.boot.psy.model.form.QuestionForm;

/**
 * 题目对象转换器
 *
 * @author liuh
 * @since 2025-10-19 18:01
 */
@Mapper(componentModel = "spring")
public interface QuestionConverter{

    QuestionForm toForm(Question entity);

    Question toEntity(QuestionForm formData);


    QuestionVO toVo(Question entity);
}