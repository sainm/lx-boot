package com.lx.boot.psy.converter;

import org.mapstruct.Mapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.entity.Dimension;
import com.lx.boot.psy.model.form.DimensionForm;

/**
 * 维度对象转换器
 *
 * @author liuh
 * @since 2025-10-19 18:01
 */
@Mapper(componentModel = "spring")
public interface DimensionConverter{

    DimensionForm toForm(Dimension entity);

    Dimension toEntity(DimensionForm formData);
}