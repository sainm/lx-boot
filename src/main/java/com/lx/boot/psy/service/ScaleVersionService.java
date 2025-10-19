package com.lx.boot.psy.service;

import com.lx.boot.psy.model.entity.ScaleVersion;
import com.lx.boot.psy.model.form.ScaleVersionForm;
import com.lx.boot.psy.model.query.ScaleVersionQuery;
import com.lx.boot.psy.model.vo.ScaleVersionVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 量版本服务类
 *
 * @author liuh
 * @since 2025-10-19 18:02
 */
public interface ScaleVersionService extends IService<ScaleVersion> {

    /**
     *量版本分页列表
     *
     * @return {@link IPage<ScaleVersionVO>} 量版本分页列表
     */
    IPage<ScaleVersionVO> getScaleVersionPage(ScaleVersionQuery queryParams);

    /**
     * 获取量版本表单数据
     *
     * @param id 量版本ID
     * @return 量版本表单数据
     */
     ScaleVersionForm getScaleVersionFormData(Long id);

    /**
     * 新增量版本
     *
     * @param formData 量版本表单对象
     * @return 是否新增成功
     */
    boolean saveScaleVersion(ScaleVersionForm formData);

    /**
     * 修改量版本
     *
     * @param id   量版本ID
     * @param formData 量版本表单对象
     * @return 是否修改成功
     */
    boolean updateScaleVersion(Long id, ScaleVersionForm formData);

    /**
     * 删除量版本
     *
     * @param ids 量版本ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    boolean deleteScaleVersions(String ids);

}
