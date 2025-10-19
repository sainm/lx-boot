package com.lx.boot.psy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lx.boot.psy.model.entity.Warning;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.query.WarningQuery;
import com.lx.boot.psy.model.vo.WarningVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测评预警记录Mapper接口
 *
 * @author liuh
 * @since 2025-10-19 18:04
 */
@Mapper
public interface WarningMapper extends BaseMapper<Warning> {

    /**
     * 获取测评预警记录分页数据
     *
     * @param page 分页对象
     * @param queryParams 查询参数
     * @return {@link Page<WarningVO>} 测评预警记录分页列表
     */
    Page<WarningVO> getWarningPage(Page<WarningVO> page, WarningQuery queryParams);

}
