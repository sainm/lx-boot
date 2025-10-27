package com.lx.boot.psy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lx.boot.psy.model.entity.Question;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.query.QuestionQuery;
import com.lx.boot.psy.model.vo.QuestionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 题目Mapper接口
 *
 * @author liuh
 * @since 2025-10-19 18:01
 */
@Mapper
public interface QuestionMapper extends BaseMapper<Question> {

    /**
     * 获取题目分页数据
     *
     * @param page 分页对象
     * @param queryParams 查询参数
     * @return {@link Page<QuestionVO>} 题目分页列表
     */
    Page<QuestionVO> getQuestionPage(Page<QuestionVO> page, @Param("queryParams") QuestionQuery queryParams);

}
