package com.lx.boot.psy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lx.boot.psy.model.entity.AssessmentRecord;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.query.AssessmentRecordQuery;
import com.lx.boot.psy.model.vo.AssessmentRecordVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 测评记录Mapper接口
 *
 * @author liuh
 * @since 2025-10-19 18:06
 */
@Mapper
public interface AssessmentRecordMapper extends BaseMapper<AssessmentRecord> {

    /**
     * 获取测评记录分页数据
     *
     * @param page 分页对象
     * @param queryParams 查询参数
     * @return {@link Page<AssessmentRecordVO>} 测评记录分页列表
     */
    Page<AssessmentRecordVO> getAssessmentRecordPage(Page<AssessmentRecordVO> page, AssessmentRecordQuery queryParams);

}
