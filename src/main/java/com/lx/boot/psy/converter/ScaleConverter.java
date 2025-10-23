package com.lx.boot.psy.converter;

import org.mapstruct.Mapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.entity.Scale;
import com.lx.boot.psy.model.form.ScaleForm;

/**
 * 量表对象转换器
 *
 * @author liuh
 * @since 2025-10-23 21:39
 */
@Mapper(componentModel = "spring")
public interface ScaleConverter{

    ScaleForm toForm(Scale entity);

    Scale toEntity(ScaleForm formData);
}