package com.lx.boot.psy.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.boot.psy.mapper.UserAnswerMapper;
import com.lx.boot.psy.service.UserAnswerService;
import com.lx.boot.psy.model.entity.UserAnswer;
import com.lx.boot.psy.model.form.UserAnswerForm;
import com.lx.boot.psy.model.query.UserAnswerQuery;
import com.lx.boot.psy.model.vo.UserAnswerVO;
import com.lx.boot.psy.converter.UserAnswerConverter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;

/**
 * 用户答题记录服务实现类
 *
 * @author liuh
 * @since 2025-10-19 18:03
 */
@Service
@RequiredArgsConstructor
public class UserAnswerServiceImpl extends ServiceImpl<UserAnswerMapper, UserAnswer> implements UserAnswerService {

    private final UserAnswerConverter userAnswerConverter;

    /**
    * 获取用户答题记录分页列表
    *
    * @param queryParams 查询参数
    * @return {@link IPage<UserAnswerVO>} 用户答题记录分页列表
    */
    @Override
    public IPage<UserAnswerVO> getUserAnswerPage(UserAnswerQuery queryParams) {
        Page<UserAnswerVO> pageVO = this.baseMapper.getUserAnswerPage(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams
        );
        return pageVO;
    }
    
    /**
     * 获取用户答题记录表单数据
     *
     * @param id 用户答题记录ID
     * @return 用户答题记录表单数据
     */
    @Override
    public UserAnswerForm getUserAnswerFormData(Long id) {
        UserAnswer entity = this.getById(id);
        return userAnswerConverter.toForm(entity);
    }
    
    /**
     * 新增用户答题记录
     *
     * @param formData 用户答题记录表单对象
     * @return 是否新增成功
     */
    @Override
    public boolean saveUserAnswer(UserAnswerForm formData) {
        UserAnswer entity = userAnswerConverter.toEntity(formData);
        return this.save(entity);
    }
    
    /**
     * 更新用户答题记录
     *
     * @param id   用户答题记录ID
     * @param formData 用户答题记录表单对象
     * @return 是否修改成功
     */
    @Override
    public boolean updateUserAnswer(Long id,UserAnswerForm formData) {
        UserAnswer entity = userAnswerConverter.toEntity(formData);
        return this.updateById(entity);
    }
    
    /**
     * 删除用户答题记录
     *
     * @param ids 用户答题记录ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    @Override
    public boolean deleteUserAnswers(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的用户答题记录数据为空");
        // 逻辑删除
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .toList();
        return this.removeByIds(idList);
    }

}
