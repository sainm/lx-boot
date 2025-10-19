package com.lx.boot.psy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lx.boot.psy.model.entity.Report;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.query.ReportQuery;
import com.lx.boot.psy.model.vo.ReportVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测评报告Mapper接口
 *
 * @author liuh
 * @since 2025-10-19 18:05
 */
@Mapper
public interface ReportMapper extends BaseMapper<Report> {

    /**
     * 获取测评报告分页数据
     *
     * @param page 分页对象
     * @param queryParams 查询参数
     * @return {@link Page<ReportVO>} 测评报告分页列表
     */
    Page<ReportVO> getReportPage(Page<ReportVO> page, ReportQuery queryParams);

}
