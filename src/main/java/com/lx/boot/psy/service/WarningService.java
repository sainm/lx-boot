package com.lx.boot.psy.service;

import com.lx.boot.psy.model.entity.Warning;
import com.lx.boot.psy.model.form.WarningForm;
import com.lx.boot.psy.model.query.WarningQuery;
import com.lx.boot.psy.model.vo.WarningVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 测评预警记录服务类
 *
 * @author liuh
 * @since 2025-10-24 14:42
 */
public interface WarningService extends IService<Warning> {

    /**
     *测评预警记录分页列表
     *
     * @return {@link IPage<WarningVO>} 测评预警记录分页列表
     */
    IPage<WarningVO> getWarningPage(WarningQuery queryParams);

    /**
     * 获取测评预警记录表单数据
     *
     * @param id 测评预警记录ID
     * @return 测评预警记录表单数据
     */
     WarningForm getWarningFormData(Long id);

    /**
     * 新增测评预警记录
     *
     * @param formData 测评预警记录表单对象
     * @return 是否新增成功
     */
    boolean saveWarning(WarningForm formData);

    /**
     * 修改测评预警记录
     *
     * @param id   测评预警记录ID
     * @param formData 测评预警记录表单对象
     * @return 是否修改成功
     */
    boolean updateWarning(Long id, WarningForm formData);

    /**
     * 删除测评预警记录
     *
     * @param ids 测评预警记录ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    boolean deleteWarnings(String ids);

}
