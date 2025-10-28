package com.lx.boot.psy.converter;

import org.mapstruct.Mapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.entity.NormScoreDistribution;
import com.lx.boot.psy.model.form.NormScoreDistributionForm;

/**
 * 常模分数分布对象转换器
 *
 * @author liuh
 * @since 2025-10-28 13:04
 */
@Mapper(componentModel = "spring")
public interface NormScoreDistributionConverter{

    NormScoreDistributionForm toForm(NormScoreDistribution entity);

    NormScoreDistribution toEntity(NormScoreDistributionForm formData);
}