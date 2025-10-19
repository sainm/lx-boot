package com.lx.boot.psy.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.boot.psy.mapper.ScoringRuleMapper;
import com.lx.boot.psy.service.ScoringRuleService;
import com.lx.boot.psy.model.entity.ScoringRule;
import com.lx.boot.psy.model.form.ScoringRuleForm;
import com.lx.boot.psy.model.query.ScoringRuleQuery;
import com.lx.boot.psy.model.vo.ScoringRuleVO;
import com.lx.boot.psy.converter.ScoringRuleConverter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;

/**
 * 计分规则主（维度）服务实现类
 *
 * @author liuh
 * @since 2025-10-19 17:59
 */
@Service
@RequiredArgsConstructor
public class ScoringRuleServiceImpl extends ServiceImpl<ScoringRuleMapper, ScoringRule> implements ScoringRuleService {

    private final ScoringRuleConverter scoringRuleConverter;

    /**
    * 获取计分规则主（维度）分页列表
    *
    * @param queryParams 查询参数
    * @return {@link IPage<ScoringRuleVO>} 计分规则主（维度）分页列表
    */
    @Override
    public IPage<ScoringRuleVO> getScoringRulePage(ScoringRuleQuery queryParams) {
        Page<ScoringRuleVO> pageVO = this.baseMapper.getScoringRulePage(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams
        );
        return pageVO;
    }
    
    /**
     * 获取计分规则主（维度）表单数据
     *
     * @param id 计分规则主（维度）ID
     * @return 计分规则主（维度）表单数据
     */
    @Override
    public ScoringRuleForm getScoringRuleFormData(Long id) {
        ScoringRule entity = this.getById(id);
        return scoringRuleConverter.toForm(entity);
    }
    
    /**
     * 新增计分规则主（维度）
     *
     * @param formData 计分规则主（维度）表单对象
     * @return 是否新增成功
     */
    @Override
    public boolean saveScoringRule(ScoringRuleForm formData) {
        ScoringRule entity = scoringRuleConverter.toEntity(formData);
        return this.save(entity);
    }
    
    /**
     * 更新计分规则主（维度）
     *
     * @param id   计分规则主（维度）ID
     * @param formData 计分规则主（维度）表单对象
     * @return 是否修改成功
     */
    @Override
    public boolean updateScoringRule(Long id,ScoringRuleForm formData) {
        ScoringRule entity = scoringRuleConverter.toEntity(formData);
        return this.updateById(entity);
    }
    
    /**
     * 删除计分规则主（维度）
     *
     * @param ids 计分规则主（维度）ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    @Override
    public boolean deleteScoringRules(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的计分规则主（维度）数据为空");
        // 逻辑删除
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .toList();
        return this.removeByIds(idList);
    }

}
