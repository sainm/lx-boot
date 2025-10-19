package com.lx.boot.psy.converter;

import org.mapstruct.Mapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.entity.AssessmentPlan;
import com.lx.boot.psy.model.form.AssessmentPlanForm;

/**
 * 测评计划对象转换器
 *
 * @author liuh
 * @since 2025-10-19 18:03
 */
@Mapper(componentModel = "spring")
public interface AssessmentPlanConverter{

    AssessmentPlanForm toForm(AssessmentPlan entity);

    AssessmentPlan toEntity(AssessmentPlanForm formData);
}