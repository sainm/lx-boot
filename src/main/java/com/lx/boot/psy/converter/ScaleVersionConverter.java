package com.lx.boot.psy.converter;

import org.mapstruct.Mapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.entity.ScaleVersion;
import com.lx.boot.psy.model.form.ScaleVersionForm;

/**
 * 量版本对象转换器
 *
 * @author liuh
 * @since 2025-10-19 18:02
 */
@Mapper(componentModel = "spring")
public interface ScaleVersionConverter{

    ScaleVersionForm toForm(ScaleVersion entity);

    ScaleVersion toEntity(ScaleVersionForm formData);
}