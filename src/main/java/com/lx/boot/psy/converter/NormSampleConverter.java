package com.lx.boot.psy.converter;

import org.mapstruct.Mapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.entity.NormSample;
import com.lx.boot.psy.model.form.NormSampleForm;

/**
 * 量常模样本定义对象转换器
 *
 * @author liuh
 * @since 2025-10-28 12:27
 */
@Mapper(componentModel = "spring")
public interface NormSampleConverter{

    NormSampleForm toForm(NormSample entity);

    NormSample toEntity(NormSampleForm formData);
}