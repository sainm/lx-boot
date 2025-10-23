package com.lx.boot.psy.converter;

import org.mapstruct.Mapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.entity.AssessmentAssignment;
import com.lx.boot.psy.model.form.AssessmentAssignmentForm;

/**
 * 测评任务分配对象转换器
 *
 * @author liuh
 * @since 2025-10-23 22:34
 */
@Mapper(componentModel = "spring")
public interface AssessmentAssignmentConverter{

    AssessmentAssignmentForm toForm(AssessmentAssignment entity);

    AssessmentAssignment toEntity(AssessmentAssignmentForm formData);
}