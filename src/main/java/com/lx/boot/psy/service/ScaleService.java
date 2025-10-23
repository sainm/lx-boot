package com.lx.boot.psy.service;

import com.lx.boot.psy.model.entity.Scale;
import com.lx.boot.psy.model.form.ScaleForm;
import com.lx.boot.psy.model.query.ScaleQuery;
import com.lx.boot.psy.model.vo.ScaleVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 量表服务类
 *
 * @author liuh
 * @since 2025-10-23 21:39
 */
public interface ScaleService extends IService<Scale> {

    /**
     *量表分页列表
     *
     * @return {@link IPage<ScaleVO>} 量表分页列表
     */
    IPage<ScaleVO> getScalePage(ScaleQuery queryParams);

    /**
     * 获取量表表单数据
     *
     * @param id 量表ID
     * @return 量表表单数据
     */
     ScaleForm getScaleFormData(Long id);

    /**
     * 新增量表
     *
     * @param formData 量表表单对象
     * @return 是否新增成功
     */
    boolean saveScale(ScaleForm formData);

    /**
     * 修改量表
     *
     * @param id   量表ID
     * @param formData 量表表单对象
     * @return 是否修改成功
     */
    boolean updateScale(Long id, ScaleForm formData);

    /**
     * 删除量表
     *
     * @param ids 量表ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    boolean deleteScales(String ids);

}
