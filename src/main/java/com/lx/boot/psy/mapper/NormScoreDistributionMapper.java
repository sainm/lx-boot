package com.lx.boot.psy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lx.boot.psy.model.entity.NormScoreDistribution;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.query.NormScoreDistributionQuery;
import com.lx.boot.psy.model.vo.NormScoreDistributionVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 常模分数分布Mapper接口
 *
 * @author liuh
 * @since 2025-10-28 13:04
 */
@Mapper
public interface NormScoreDistributionMapper extends BaseMapper<NormScoreDistribution> {

    /**
     * 获取常模分数分布分页数据
     *
     * @param page 分页对象
     * @param queryParams 查询参数
     * @return {@link Page<NormScoreDistributionVO>} 常模分数分布分页列表
     */
    Page<NormScoreDistributionVO> getNormScoreDistributionPage(Page<NormScoreDistributionVO> page, NormScoreDistributionQuery queryParams);

}
