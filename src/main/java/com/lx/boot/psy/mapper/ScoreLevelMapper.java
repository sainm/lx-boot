package com.lx.boot.psy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lx.boot.psy.model.entity.ScoreLevel;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.query.ScoreLevelQuery;
import com.lx.boot.psy.model.vo.ScoreLevelVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分数区间对应等级描述Mapper接口
 *
 * @author liuh
 * @since 2025-10-19 17:58
 */
@Mapper
public interface ScoreLevelMapper extends BaseMapper<ScoreLevel> {

    /**
     * 获取分数区间对应等级描述分页数据
     *
     * @param page 分页对象
     * @param queryParams 查询参数
     * @return {@link Page<ScoreLevelVO>} 分数区间对应等级描述分页列表
     */
    Page<ScoreLevelVO> getScoreLevelPage(Page<ScoreLevelVO> page, ScoreLevelQuery queryParams);

}
