package com.lx.boot.psy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lx.boot.psy.model.entity.Dimension;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.query.DimensionQuery;
import com.lx.boot.psy.model.vo.DimensionVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 维度Mapper接口
 *
 * @author liuh
 * @since 2025-10-19 18:01
 */
@Mapper
public interface DimensionMapper extends BaseMapper<Dimension> {

    /**
     * 获取维度分页数据
     *
     * @param page 分页对象
     * @param queryParams 查询参数
     * @return {@link Page<DimensionVO>} 维度分页列表
     */
    Page<DimensionVO> getDimensionPage(Page<DimensionVO> page, DimensionQuery queryParams);

}
