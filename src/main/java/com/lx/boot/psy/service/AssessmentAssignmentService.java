package com.lx.boot.psy.service;

import com.lx.boot.psy.model.entity.AssessmentAssignment;
import com.lx.boot.psy.model.form.AssessmentAssignmentForm;
import com.lx.boot.psy.model.query.AssessmentAssignmentQuery;
import com.lx.boot.psy.model.vo.AssessmentAssignmentVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 测评任务分配服务类
 *
 * @author liuh
 * @since 2025-10-23 22:34
 */
public interface AssessmentAssignmentService extends IService<AssessmentAssignment> {

    /**
     *测评任务分配分页列表
     *
     * @return {@link IPage<AssessmentAssignmentVO>} 测评任务分配分页列表
     */
    IPage<AssessmentAssignmentVO> getAssessmentAssignmentPage(AssessmentAssignmentQuery queryParams);

    /**
     * 获取测评任务分配表单数据
     *
     * @param id 测评任务分配ID
     * @return 测评任务分配表单数据
     */
     AssessmentAssignmentForm getAssessmentAssignmentFormData(Long id);

    /**
     * 新增测评任务分配
     *
     * @param formData 测评任务分配表单对象
     * @return 是否新增成功
     */
    boolean saveAssessmentAssignment(AssessmentAssignmentForm formData);

    /**
     * 修改测评任务分配
     *
     * @param id   测评任务分配ID
     * @param formData 测评任务分配表单对象
     * @return 是否修改成功
     */
    boolean updateAssessmentAssignment(Long id, AssessmentAssignmentForm formData);

    /**
     * 删除测评任务分配
     *
     * @param ids 测评任务分配ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    boolean deleteAssessmentAssignments(String ids);

}
