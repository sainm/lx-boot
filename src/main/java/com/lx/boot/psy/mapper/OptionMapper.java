package com.lx.boot.psy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lx.boot.psy.model.entity.Option;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.psy.model.query.OptionQuery;
import com.lx.boot.psy.model.vo.OptionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 题目选项Mapper接口
 *
 * @author liuh
 * @since 2025-10-19 17:59
 */
@Mapper
public interface OptionMapper extends BaseMapper<Option> {

    /**
     * 获取题目选项分页数据
     *
     * @param page 分页对象
     * @param queryParams 查询参数
     * @return {@link Page<OptionVO>} 题目选项分页列表
     */
    Page<OptionVO> getOptionPage(Page<OptionVO> page, @Param("queryParams") OptionQuery queryParams);

    List<OptionVO> getOptions(@Param("questionId") Long id);
}
