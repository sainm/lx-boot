package com.lx.boot.psy.service;

import com.lx.boot.psy.model.entity.NormSample;
import com.lx.boot.psy.model.form.NormSampleForm;
import com.lx.boot.psy.model.query.NormSampleQuery;
import com.lx.boot.psy.model.vo.NormSampleVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 量常模样本定义服务类
 *
 * @author liuh
 * @since 2025-10-28 12:27
 */
public interface NormSampleService extends IService<NormSample> {

    /**
     *量常模样本定义分页列表
     *
     * @return {@link IPage<NormSampleVO>} 量常模样本定义分页列表
     */
    IPage<NormSampleVO> getNormSamplePage(NormSampleQuery queryParams);

    /**
     * 获取量常模样本定义表单数据
     *
     * @param id 量常模样本定义ID
     * @return 量常模样本定义表单数据
     */
     NormSampleForm getNormSampleFormData(Long id);

    /**
     * 新增量常模样本定义
     *
     * @param formData 量常模样本定义表单对象
     * @return 是否新增成功
     */
    boolean saveNormSample(NormSampleForm formData);

    /**
     * 修改量常模样本定义
     *
     * @param id   量常模样本定义ID
     * @param formData 量常模样本定义表单对象
     * @return 是否修改成功
     */
    boolean updateNormSample(Long id, NormSampleForm formData);

    /**
     * 删除量常模样本定义
     *
     * @param ids 量常模样本定义ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    boolean deleteNormSamples(String ids);

}
