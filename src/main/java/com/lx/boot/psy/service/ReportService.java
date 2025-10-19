package com.lx.boot.psy.service;

import com.lx.boot.psy.model.entity.Report;
import com.lx.boot.psy.model.form.ReportForm;
import com.lx.boot.psy.model.query.ReportQuery;
import com.lx.boot.psy.model.vo.ReportVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 测评报告服务类
 *
 * @author liuh
 * @since 2025-10-19 18:05
 */
public interface ReportService extends IService<Report> {

    /**
     *测评报告分页列表
     *
     * @return {@link IPage<ReportVO>} 测评报告分页列表
     */
    IPage<ReportVO> getReportPage(ReportQuery queryParams);

    /**
     * 获取测评报告表单数据
     *
     * @param id 测评报告ID
     * @return 测评报告表单数据
     */
     ReportForm getReportFormData(Long id);

    /**
     * 新增测评报告
     *
     * @param formData 测评报告表单对象
     * @return 是否新增成功
     */
    boolean saveReport(ReportForm formData);

    /**
     * 修改测评报告
     *
     * @param id   测评报告ID
     * @param formData 测评报告表单对象
     * @return 是否修改成功
     */
    boolean updateReport(Long id, ReportForm formData);

    /**
     * 删除测评报告
     *
     * @param ids 测评报告ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    boolean deleteReports(String ids);

}
