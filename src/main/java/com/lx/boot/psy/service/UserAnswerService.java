package com.lx.boot.psy.service;

import com.lx.boot.psy.model.entity.UserAnswer;
import com.lx.boot.psy.model.form.UserAnswerForm;
import com.lx.boot.psy.model.query.UserAnswerQuery;
import com.lx.boot.psy.model.vo.UserAnswerVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 用户答题记录服务类
 *
 * @author liuh
 * @since 2025-10-24 14:41
 */
public interface UserAnswerService extends IService<UserAnswer> {

    /**
     *用户答题记录分页列表
     *
     * @return {@link IPage<UserAnswerVO>} 用户答题记录分页列表
     */
    IPage<UserAnswerVO> getUserAnswerPage(UserAnswerQuery queryParams);

    /**
     * 获取用户答题记录表单数据
     *
     * @param id 用户答题记录ID
     * @return 用户答题记录表单数据
     */
     UserAnswerForm getUserAnswerFormData(Long id);

    /**
     * 新增用户答题记录
     *
     * @param formData 用户答题记录表单对象
     * @return 是否新增成功
     */
    boolean saveUserAnswer(UserAnswerForm formData);

    /**
     * 修改用户答题记录
     *
     * @param id   用户答题记录ID
     * @param formData 用户答题记录表单对象
     * @return 是否修改成功
     */
    boolean updateUserAnswer(Long id, UserAnswerForm formData);

    /**
     * 删除用户答题记录
     *
     * @param ids 用户答题记录ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    boolean deleteUserAnswers(String ids);

    List<UserAnswer> getByRecordId(Long recordId);

    void saveOrUpdateAnswer(UserAnswer userAnswer);
}
