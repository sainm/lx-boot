package com.lx.boot.psy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lx.boot.psy.model.entity.ScaleVersion;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.query.ScaleVersionQuery;
import com.lx.boot.psy.model.vo.ScaleVersionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 量版本Mapper接口
 *
 * @author liuh
 * @since 2025-10-19 18:02
 */
@Mapper
public interface ScaleVersionMapper extends BaseMapper<ScaleVersion> {

    /**
     * 获取量版本分页数据
     *
     * @param page 分页对象
     * @param queryParams 查询参数
     * @return {@link Page<ScaleVersionVO>} 量版本分页列表
     */
    Page<ScaleVersionVO> getScaleVersionPage(Page<ScaleVersionVO> page, @Param("queryParams") ScaleVersionQuery queryParams);

}
