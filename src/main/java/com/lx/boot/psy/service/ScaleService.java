package com.lx.boot.psy.service;

import com.lx.boot.psy.model.entity.Scale;
import com.lx.boot.psy.model.form.ScaleForm;
import com.lx.boot.psy.model.query.ScaleQuery;
import com.lx.boot.psy.model.vo.ScaleVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 量主服务类
 *
 * @author liuh
 * @since 2025-10-19 18:02
 */
public interface ScaleService extends IService<Scale> {

    /**
     *量主分页列表
     *
     * @return {@link IPage<ScaleVO>} 量主分页列表
     */
    IPage<ScaleVO> getScalePage(ScaleQuery queryParams);

    /**
     * 获取量主表单数据
     *
     * @param id 量主ID
     * @return 量主表单数据
     */
     ScaleForm getScaleFormData(Long id);

    /**
     * 新增量主
     *
     * @param formData 量主表单对象
     * @return 是否新增成功
     */
    boolean saveScale(ScaleForm formData);

    /**
     * 修改量主
     *
     * @param id   量主ID
     * @param formData 量主表单对象
     * @return 是否修改成功
     */
    boolean updateScale(Long id, ScaleForm formData);

    /**
     * 删除量主
     *
     * @param ids 量主ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    boolean deleteScales(String ids);

}
