package com.lx.boot.psy.converter;

import org.mapstruct.Mapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.entity.Option;
import com.lx.boot.psy.model.form.OptionForm;

/**
 * 题目选项对象转换器
 *
 * @author liuh
 * @since 2025-10-19 17:59
 */
@Mapper(componentModel = "spring")
public interface OptionConverter{

    OptionForm toForm(Option entity);

    Option toEntity(OptionForm formData);
}