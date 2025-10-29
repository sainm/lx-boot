package com.lx.boot.psy.service;

import com.lx.boot.psy.model.entity.AssessmentRecord;
import com.lx.boot.psy.model.form.AssessmentRecordForm;
import com.lx.boot.psy.model.query.AssessmentRecordQuery;
import com.lx.boot.psy.model.vo.AssessmentRecordVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 测评记录服务类
 *
 * @author liuh
 * @since 2025-10-24 14:44
 */
public interface AssessmentRecordService extends IService<AssessmentRecord> {

    /**
     *测评记录分页列表
     *
     * @return {@link IPage<AssessmentRecordVO>} 测评记录分页列表
     */
    IPage<AssessmentRecordVO> getAssessmentRecordPage(AssessmentRecordQuery queryParams);

    /**
     * 获取测评记录表单数据
     *
     * @param id 测评记录ID
     * @return 测评记录表单数据
     */
     AssessmentRecordForm getAssessmentRecordFormData(Long id);

    /**
     * 新增测评记录
     *
     * @param formData 测评记录表单对象
     * @return 是否新增成功
     */
    boolean saveAssessmentRecord(AssessmentRecordForm formData);

    /**
     * 修改测评记录
     *
     * @param id   测评记录ID
     * @param formData 测评记录表单对象
     * @return 是否修改成功
     */
    boolean updateAssessmentRecord(Long id, AssessmentRecordForm formData);

    /**
     * 删除测评记录
     *
     * @param ids 测评记录ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    boolean deleteAssessmentRecords(String ids);

    void submitAssessment(Long recordId);
}
