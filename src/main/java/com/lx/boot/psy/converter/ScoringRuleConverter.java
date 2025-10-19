package com.lx.boot.psy.converter;

import org.mapstruct.Mapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.entity.ScoringRule;
import com.lx.boot.psy.model.form.ScoringRuleForm;

/**
 * 计分规则主（维度）对象转换器
 *
 * @author liuh
 * @since 2025-10-19 17:59
 */
@Mapper(componentModel = "spring")
public interface ScoringRuleConverter{

    ScoringRuleForm toForm(ScoringRule entity);

    ScoringRule toEntity(ScoringRuleForm formData);
}