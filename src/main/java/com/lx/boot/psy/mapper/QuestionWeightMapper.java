package com.lx.boot.psy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lx.boot.psy.model.entity.QuestionWeight;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.query.QuestionWeightQuery;
import com.lx.boot.psy.model.vo.QuestionWeightVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计分规则题目权重Mapper接口
 *
 * @author liuh
 * @since 2025-10-28 11:35
 */
@Mapper
public interface QuestionWeightMapper extends BaseMapper<QuestionWeight> {

    /**
     * 获取计分规则题目权重分页数据
     *
     * @param page 分页对象
     * @param queryParams 查询参数
     * @return {@link Page<QuestionWeightVO>} 计分规则题目权重分页列表
     */
    Page<QuestionWeightVO> getQuestionWeightPage(Page<QuestionWeightVO> page, QuestionWeightQuery queryParams);

}
