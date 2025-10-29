package com.lx.boot.psy.service;

import com.lx.boot.psy.model.entity.Option;
import com.lx.boot.psy.model.form.OptionForm;
import com.lx.boot.psy.model.query.OptionQuery;
import com.lx.boot.psy.model.vo.OptionVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 题目选项服务类
 *
 * @author liuh
 * @since 2025-10-19 17:59
 */
public interface OptionService extends IService<Option> {

    /**
     *题目选项分页列表
     *
     * @return {@link IPage<OptionVO>} 题目选项分页列表
     */
    IPage<OptionVO> getOptionPage(OptionQuery queryParams);

    /**
     * 获取题目选项表单数据
     *
     * @param id 题目选项ID
     * @return 题目选项表单数据
     */
     OptionForm getOptionFormData(Long id);

    /**
     * 新增题目选项
     *
     * @param formData 题目选项表单对象
     * @return 是否新增成功
     */
    boolean saveOption(OptionForm formData);

    /**
     * 修改题目选项
     *
     * @param id   题目选项ID
     * @param formData 题目选项表单对象
     * @return 是否修改成功
     */
    boolean updateOption(Long id, OptionForm formData);

    /**
     * 删除题目选项
     *
     * @param ids 题目选项ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    boolean deleteOptions(String ids);

    List<OptionVO> listByQuestionId(Long id);
}
