package com.lx.boot.psy.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.boot.psy.mapper.QuestionMapper;
import com.lx.boot.psy.service.QuestionService;
import com.lx.boot.psy.model.entity.Question;
import com.lx.boot.psy.model.form.QuestionForm;
import com.lx.boot.psy.model.query.QuestionQuery;
import com.lx.boot.psy.model.vo.QuestionVO;
import com.lx.boot.psy.converter.QuestionConverter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;

/**
 * 题目服务实现类
 *
 * @author liuh
 * @since 2025-10-19 18:01
 */
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    private final QuestionConverter questionConverter;

    /**
    * 获取题目分页列表
    *
    * @param queryParams 查询参数
    * @return {@link IPage<QuestionVO>} 题目分页列表
    */
    @Override
    public IPage<QuestionVO> getQuestionPage(QuestionQuery queryParams) {
        Page<QuestionVO> pageVO = this.baseMapper.getQuestionPage(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams
        );
        return pageVO;
    }
    
    /**
     * 获取题目表单数据
     *
     * @param id 题目ID
     * @return 题目表单数据
     */
    @Override
    public QuestionForm getQuestionFormData(Long id) {
        Question entity = this.getById(id);
        return questionConverter.toForm(entity);
    }
    
    /**
     * 新增题目
     *
     * @param formData 题目表单对象
     * @return 是否新增成功
     */
    @Override
    public boolean saveQuestion(QuestionForm formData) {
        Question entity = questionConverter.toEntity(formData);
        return this.save(entity);
    }
    
    /**
     * 更新题目
     *
     * @param id   题目ID
     * @param formData 题目表单对象
     * @return 是否修改成功
     */
    @Override
    public boolean updateQuestion(Long id,QuestionForm formData) {
        Question entity = questionConverter.toEntity(formData);
        return this.updateById(entity);
    }
    
    /**
     * 删除题目
     *
     * @param ids 题目ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    @Override
    public boolean deleteQuestions(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的题目数据为空");
        // 逻辑删除
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .toList();
        return this.removeByIds(idList);
    }

}
