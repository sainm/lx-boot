package com.lx.boot.psy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lx.boot.psy.model.entity.UserAnswer;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.query.UserAnswerQuery;
import com.lx.boot.psy.model.vo.UserAnswerVO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户答题记录Mapper接口
 *
 * @author liuh
 * @since 2025-10-19 18:03
 */
@Mapper
public interface UserAnswerMapper extends BaseMapper<UserAnswer> {

    /**
     * 获取用户答题记录分页数据
     *
     * @param page 分页对象
     * @param queryParams 查询参数
     * @return {@link Page<UserAnswerVO>} 用户答题记录分页列表
     */
    Page<UserAnswerVO> getUserAnswerPage(Page<UserAnswerVO> page, UserAnswerQuery queryParams);

}
