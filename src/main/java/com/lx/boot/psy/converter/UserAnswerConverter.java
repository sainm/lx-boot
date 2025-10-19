package com.lx.boot.psy.converter;

import org.mapstruct.Mapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.entity.UserAnswer;
import com.lx.boot.psy.model.form.UserAnswerForm;

/**
 * 用户答题记录对象转换器
 *
 * @author liuh
 * @since 2025-10-19 18:03
 */
@Mapper(componentModel = "spring")
public interface UserAnswerConverter{

    UserAnswerForm toForm(UserAnswer entity);

    UserAnswer toEntity(UserAnswerForm formData);
}