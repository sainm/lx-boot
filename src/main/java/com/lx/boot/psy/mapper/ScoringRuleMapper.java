package com.lx.boot.psy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lx.boot.psy.model.entity.ScoringRule;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.query.ScoringRuleQuery;
import com.lx.boot.psy.model.vo.ScoringRuleVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计分规则主（维度）Mapper接口
 *
 * @author liuh
 * @since 2025-10-19 17:59
 */
@Mapper
public interface ScoringRuleMapper extends BaseMapper<ScoringRule> {

    /**
     * 获取计分规则主（维度）分页数据
     *
     * @param page 分页对象
     * @param queryParams 查询参数
     * @return {@link Page<ScoringRuleVO>} 计分规则主（维度）分页列表
     */
    Page<ScoringRuleVO> getScoringRulePage(Page<ScoringRuleVO> page, ScoringRuleQuery queryParams);

}
