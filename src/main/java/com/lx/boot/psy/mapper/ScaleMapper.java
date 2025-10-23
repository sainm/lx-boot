package com.lx.boot.psy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lx.boot.psy.model.entity.Scale;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.query.ScaleQuery;
import com.lx.boot.psy.model.vo.ScaleVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 量表Mapper接口
 *
 * @author liuh
 * @since 2025-10-23 21:39
 */
@Mapper
public interface ScaleMapper extends BaseMapper<Scale> {

    /**
     * 获取量表分页数据
     *
     * @param page 分页对象
     * @param queryParams 查询参数
     * @return {@link Page<ScaleVO>} 量表分页列表
     */
    Page<ScaleVO> getScalePage(Page<ScaleVO> page, ScaleQuery queryParams);

}
