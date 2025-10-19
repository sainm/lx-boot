package com.lx.boot.psy.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.boot.psy.mapper.ReportMapper;
import com.lx.boot.psy.service.ReportService;
import com.lx.boot.psy.model.entity.Report;
import com.lx.boot.psy.model.form.ReportForm;
import com.lx.boot.psy.model.query.ReportQuery;
import com.lx.boot.psy.model.vo.ReportVO;
import com.lx.boot.psy.converter.ReportConverter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;

/**
 * 测评报告服务实现类
 *
 * @author liuh
 * @since 2025-10-19 18:05
 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl extends ServiceImpl<ReportMapper, Report> implements ReportService {

    private final ReportConverter reportConverter;

    /**
    * 获取测评报告分页列表
    *
    * @param queryParams 查询参数
    * @return {@link IPage<ReportVO>} 测评报告分页列表
    */
    @Override
    public IPage<ReportVO> getReportPage(ReportQuery queryParams) {
        Page<ReportVO> pageVO = this.baseMapper.getReportPage(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams
        );
        return pageVO;
    }
    
    /**
     * 获取测评报告表单数据
     *
     * @param id 测评报告ID
     * @return 测评报告表单数据
     */
    @Override
    public ReportForm getReportFormData(Long id) {
        Report entity = this.getById(id);
        return reportConverter.toForm(entity);
    }
    
    /**
     * 新增测评报告
     *
     * @param formData 测评报告表单对象
     * @return 是否新增成功
     */
    @Override
    public boolean saveReport(ReportForm formData) {
        Report entity = reportConverter.toEntity(formData);
        return this.save(entity);
    }
    
    /**
     * 更新测评报告
     *
     * @param id   测评报告ID
     * @param formData 测评报告表单对象
     * @return 是否修改成功
     */
    @Override
    public boolean updateReport(Long id,ReportForm formData) {
        Report entity = reportConverter.toEntity(formData);
        return this.updateById(entity);
    }
    
    /**
     * 删除测评报告
     *
     * @param ids 测评报告ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    @Override
    public boolean deleteReports(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的测评报告数据为空");
        // 逻辑删除
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .toList();
        return this.removeByIds(idList);
    }

}
