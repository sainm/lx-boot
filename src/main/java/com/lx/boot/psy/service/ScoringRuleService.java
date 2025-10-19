package com.lx.boot.psy.service;

import com.lx.boot.psy.model.entity.ScoringRule;
import com.lx.boot.psy.model.form.ScoringRuleForm;
import com.lx.boot.psy.model.query.ScoringRuleQuery;
import com.lx.boot.psy.model.vo.ScoringRuleVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 计分规则主（维度）服务类
 *
 * @author liuh
 * @since 2025-10-19 17:59
 */
public interface ScoringRuleService extends IService<ScoringRule> {

    /**
     *计分规则主（维度）分页列表
     *
     * @return {@link IPage<ScoringRuleVO>} 计分规则主（维度）分页列表
     */
    IPage<ScoringRuleVO> getScoringRulePage(ScoringRuleQuery queryParams);

    /**
     * 获取计分规则主（维度）表单数据
     *
     * @param id 计分规则主（维度）ID
     * @return 计分规则主（维度）表单数据
     */
     ScoringRuleForm getScoringRuleFormData(Long id);

    /**
     * 新增计分规则主（维度）
     *
     * @param formData 计分规则主（维度）表单对象
     * @return 是否新增成功
     */
    boolean saveScoringRule(ScoringRuleForm formData);

    /**
     * 修改计分规则主（维度）
     *
     * @param id   计分规则主（维度）ID
     * @param formData 计分规则主（维度）表单对象
     * @return 是否修改成功
     */
    boolean updateScoringRule(Long id, ScoringRuleForm formData);

    /**
     * 删除计分规则主（维度）
     *
     * @param ids 计分规则主（维度）ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    boolean deleteScoringRules(String ids);

}
