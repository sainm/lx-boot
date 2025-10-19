package com.lx.boot.psy.converter;

import org.mapstruct.Mapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.entity.AssessmentRecord;
import com.lx.boot.psy.model.form.AssessmentRecordForm;

/**
 * 测评记录对象转换器
 *
 * @author liuh
 * @since 2025-10-19 18:06
 */
@Mapper(componentModel = "spring")
public interface AssessmentRecordConverter{

    AssessmentRecordForm toForm(AssessmentRecord entity);

    AssessmentRecord toEntity(AssessmentRecordForm formData);
}