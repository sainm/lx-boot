package com.lx.boot.psy.service;

import com.lx.boot.psy.model.entity.QuestionWeight;
import com.lx.boot.psy.model.form.QuestionWeightForm;
import com.lx.boot.psy.model.query.QuestionWeightQuery;
import com.lx.boot.psy.model.vo.QuestionWeightVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 计分规则题目权重服务类
 *
 * @author liuh
 * @since 2025-10-28 11:35
 */
public interface QuestionWeightService extends IService<QuestionWeight> {

    /**
     *计分规则题目权重分页列表
     *
     * @return {@link IPage<QuestionWeightVO>} 计分规则题目权重分页列表
     */
    IPage<QuestionWeightVO> getQuestionWeightPage(QuestionWeightQuery queryParams);

    /**
     * 获取计分规则题目权重表单数据
     *
     * @param id 计分规则题目权重ID
     * @return 计分规则题目权重表单数据
     */
     QuestionWeightForm getQuestionWeightFormData(Long id);

    /**
     * 新增计分规则题目权重
     *
     * @param formData 计分规则题目权重表单对象
     * @return 是否新增成功
     */
    boolean saveQuestionWeight(QuestionWeightForm formData);

    /**
     * 修改计分规则题目权重
     *
     * @param id   计分规则题目权重ID
     * @param formData 计分规则题目权重表单对象
     * @return 是否修改成功
     */
    boolean updateQuestionWeight(Long id, QuestionWeightForm formData);

    /**
     * 删除计分规则题目权重
     *
     * @param ids 计分规则题目权重ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    boolean deleteQuestionWeights(String ids);

}
