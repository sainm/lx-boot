package com.lx.boot.psy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lx.boot.psy.model.entity.AssessmentPlan;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.query.AssessmentPlanQuery;
import com.lx.boot.psy.model.vo.AssessmentPlanVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测评计划Mapper接口
 *
 * @author liuh
 * @since 2025-10-19 18:03
 */
@Mapper
public interface AssessmentPlanMapper extends BaseMapper<AssessmentPlan> {

    /**
     * 获取测评计划分页数据
     *
     * @param page 分页对象
     * @param queryParams 查询参数
     * @return {@link Page<AssessmentPlanVO>} 测评计划分页列表
     */
    Page<AssessmentPlanVO> getAssessmentPlanPage(Page<AssessmentPlanVO> page, AssessmentPlanQuery queryParams);

}
