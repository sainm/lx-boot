package com.lx.boot.psy.converter;

import org.mapstruct.Mapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.entity.Warning;
import com.lx.boot.psy.model.form.WarningForm;

/**
 * 测评预警记录对象转换器
 *
 * @author liuh
 * @since 2025-10-19 18:04
 */
@Mapper(componentModel = "spring")
public interface WarningConverter{

    WarningForm toForm(Warning entity);

    Warning toEntity(WarningForm formData);
}