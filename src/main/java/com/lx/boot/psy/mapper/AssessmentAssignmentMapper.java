package com.lx.boot.psy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lx.boot.psy.model.entity.AssessmentAssignment;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.query.AssessmentAssignmentQuery;
import com.lx.boot.psy.model.vo.AssessmentAssignmentVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测评任务分配Mapper接口
 *
 * @author liuh
 * @since 2025-10-19 18:05
 */
@Mapper
public interface AssessmentAssignmentMapper extends BaseMapper<AssessmentAssignment> {

    /**
     * 获取测评任务分配分页数据
     *
     * @param page 分页对象
     * @param queryParams 查询参数
     * @return {@link Page<AssessmentAssignmentVO>} 测评任务分配分页列表
     */
    Page<AssessmentAssignmentVO> getAssessmentAssignmentPage(Page<AssessmentAssignmentVO> page, AssessmentAssignmentQuery queryParams);

}
