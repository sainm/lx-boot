package com.lx.boot.psy.service;

import com.lx.boot.psy.model.entity.Question;
import com.lx.boot.psy.model.form.QuestionForm;
import com.lx.boot.psy.model.query.QuestionQuery;
import com.lx.boot.psy.model.vo.QuestionVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 题目服务类
 *
 * @author liuh
 * @since 2025-10-19 18:01
 */
public interface QuestionService extends IService<Question> {

    /**
     *题目分页列表
     *
     * @return {@link IPage<QuestionVO>} 题目分页列表
     */
    IPage<QuestionVO> getQuestionPage(QuestionQuery queryParams);

    /**
     * 获取题目表单数据
     *
     * @param id 题目ID
     * @return 题目表单数据
     */
     QuestionForm getQuestionFormData(Long id);

    /**
     * 新增题目
     *
     * @param formData 题目表单对象
     * @return 是否新增成功
     */
    boolean saveQuestion(QuestionForm formData);

    /**
     * 修改题目
     *
     * @param id   题目ID
     * @param formData 题目表单对象
     * @return 是否修改成功
     */
    boolean updateQuestion(Long id, QuestionForm formData);

    /**
     * 删除题目
     *
     * @param ids 题目ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    boolean deleteQuestions(String ids);

}
