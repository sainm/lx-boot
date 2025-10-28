package com.lx.boot.psy.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.boot.psy.mapper.QuestionWeightMapper;
import com.lx.boot.psy.service.QuestionWeightService;
import com.lx.boot.psy.model.entity.QuestionWeight;
import com.lx.boot.psy.model.form.QuestionWeightForm;
import com.lx.boot.psy.model.query.QuestionWeightQuery;
import com.lx.boot.psy.model.vo.QuestionWeightVO;
import com.lx.boot.psy.converter.QuestionWeightConverter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;

/**
 * 计分规则题目权重服务实现类
 *
 * @author liuh
 * @since 2025-10-28 11:35
 */
@Service
@RequiredArgsConstructor
public class QuestionWeightServiceImpl extends ServiceImpl<QuestionWeightMapper, QuestionWeight> implements QuestionWeightService {

    private final QuestionWeightConverter questionWeightConverter;

    /**
    * 获取计分规则题目权重分页列表
    *
    * @param queryParams 查询参数
    * @return {@link IPage<QuestionWeightVO>} 计分规则题目权重分页列表
    */
    @Override
    public IPage<QuestionWeightVO> getQuestionWeightPage(QuestionWeightQuery queryParams) {
        Page<QuestionWeightVO> pageVO = this.baseMapper.getQuestionWeightPage(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams
        );
        return pageVO;
    }
    
    /**
     * 获取计分规则题目权重表单数据
     *
     * @param id 计分规则题目权重ID
     * @return 计分规则题目权重表单数据
     */
    @Override
    public QuestionWeightForm getQuestionWeightFormData(Long id) {
        QuestionWeight entity = this.getById(id);
        return questionWeightConverter.toForm(entity);
    }
    
    /**
     * 新增计分规则题目权重
     *
     * @param formData 计分规则题目权重表单对象
     * @return 是否新增成功
     */
    @Override
    public boolean saveQuestionWeight(QuestionWeightForm formData) {
        QuestionWeight entity = questionWeightConverter.toEntity(formData);
        return this.save(entity);
    }
    
    /**
     * 更新计分规则题目权重
     *
     * @param id   计分规则题目权重ID
     * @param formData 计分规则题目权重表单对象
     * @return 是否修改成功
     */
    @Override
    public boolean updateQuestionWeight(Long id,QuestionWeightForm formData) {
        QuestionWeight entity = questionWeightConverter.toEntity(formData);
        return this.updateById(entity);
    }
    
    /**
     * 删除计分规则题目权重
     *
     * @param ids 计分规则题目权重ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    @Override
    public boolean deleteQuestionWeights(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的计分规则题目权重数据为空");
        // 逻辑删除
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .toList();
        return this.removeByIds(idList);
    }

}
