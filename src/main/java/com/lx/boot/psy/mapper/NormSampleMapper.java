package com.lx.boot.psy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lx.boot.psy.model.entity.NormSample;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.query.NormSampleQuery;
import com.lx.boot.psy.model.vo.NormSampleVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 量常模样本定义Mapper接口
 *
 * @author liuh
 * @since 2025-10-28 12:27
 */
@Mapper
public interface NormSampleMapper extends BaseMapper<NormSample> {

    /**
     * 获取量常模样本定义分页数据
     *
     * @param page 分页对象
     * @param queryParams 查询参数
     * @return {@link Page<NormSampleVO>} 量常模样本定义分页列表
     */
    Page<NormSampleVO> getNormSamplePage(Page<NormSampleVO> page, NormSampleQuery queryParams);

}
