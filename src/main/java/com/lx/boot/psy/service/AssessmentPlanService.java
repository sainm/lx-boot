package com.lx.boot.psy.service;

import com.lx.boot.psy.model.entity.AssessmentPlan;
import com.lx.boot.psy.model.form.AssessmentPlanForm;
import com.lx.boot.psy.model.query.AssessmentPlanQuery;
import com.lx.boot.psy.model.vo.AssessmentPlanVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 测评计划服务类
 *
 * @author liuh
 * @since 2025-10-24 14:39
 */
public interface AssessmentPlanService extends IService<AssessmentPlan> {

    /**
     *测评计划分页列表
     *
     * @return {@link IPage<AssessmentPlanVO>} 测评计划分页列表
     */
    IPage<AssessmentPlanVO> getAssessmentPlanPage(AssessmentPlanQuery queryParams);

    /**
     * 获取测评计划表单数据
     *
     * @param id 测评计划ID
     * @return 测评计划表单数据
     */
     AssessmentPlanForm getAssessmentPlanFormData(Long id);

    /**
     * 新增测评计划
     *
     * @param formData 测评计划表单对象
     * @return 是否新增成功
     */
    boolean saveAssessmentPlan(AssessmentPlanForm formData);

    /**
     * 修改测评计划
     *
     * @param id   测评计划ID
     * @param formData 测评计划表单对象
     * @return 是否修改成功
     */
    boolean updateAssessmentPlan(Long id, AssessmentPlanForm formData);

    /**
     * 删除测评计划
     *
     * @param ids 测评计划ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    boolean deleteAssessmentPlans(String ids);

}
