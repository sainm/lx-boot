package com.lx.boot.psy.service;

import com.lx.boot.psy.model.entity.Dimension;
import com.lx.boot.psy.model.form.DimensionForm;
import com.lx.boot.psy.model.query.DimensionQuery;
import com.lx.boot.psy.model.vo.DimensionVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 维度服务类
 *
 * @author liuh
 * @since 2025-10-19 18:01
 */
public interface DimensionService extends IService<Dimension> {

    /**
     *维度分页列表
     *
     * @return {@link IPage<DimensionVO>} 维度分页列表
     */
    IPage<DimensionVO> getDimensionPage(DimensionQuery queryParams);

    /**
     * 获取维度表单数据
     *
     * @param id 维度ID
     * @return 维度表单数据
     */
     DimensionForm getDimensionFormData(Long id);

    /**
     * 新增维度
     *
     * @param formData 维度表单对象
     * @return 是否新增成功
     */
    boolean saveDimension(DimensionForm formData);

    /**
     * 修改维度
     *
     * @param id   维度ID
     * @param formData 维度表单对象
     * @return 是否修改成功
     */
    boolean updateDimension(Long id, DimensionForm formData);

    /**
     * 删除维度
     *
     * @param ids 维度ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    boolean deleteDimensions(String ids);

}
