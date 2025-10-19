package com.lx.boot.psy.converter;

import org.mapstruct.Mapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.entity.Report;
import com.lx.boot.psy.model.form.ReportForm;

/**
 * 测评报告对象转换器
 *
 * @author liuh
 * @since 2025-10-19 18:05
 */
@Mapper(componentModel = "spring")
public interface ReportConverter{

    ReportForm toForm(Report entity);

    Report toEntity(ReportForm formData);
}