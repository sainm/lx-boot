package com.lx.boot.psy.service.impl;

import com.lx.boot.core.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.boot.psy.mapper.OptionMapper;
import com.lx.boot.psy.service.OptionService;
import com.lx.boot.psy.model.entity.Option;
import com.lx.boot.psy.model.form.OptionForm;
import com.lx.boot.psy.model.query.OptionQuery;
import com.lx.boot.psy.model.vo.OptionVO;
import com.lx.boot.psy.converter.OptionConverter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;

/**
 * 题目选项服务实现类
 *
 * @author liuh
 * @since 2025-10-19 17:59
 */
@Service
@RequiredArgsConstructor
public class OptionServiceImpl extends ServiceImpl<OptionMapper, Option> implements OptionService {

    private final OptionConverter optionConverter;

    /**
    * 获取题目选项分页列表
    *
    * @param queryParams 查询参数
    * @return {@link IPage<OptionVO>} 题目选项分页列表
    */
    @Override
    public IPage<OptionVO> getOptionPage(OptionQuery queryParams) {
        Page<OptionVO> pageVO = this.baseMapper.getOptionPage(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams
        );
        return pageVO;
    }
    
    /**
     * 获取题目选项表单数据
     *
     * @param id 题目选项ID
     * @return 题目选项表单数据
     */
    @Override
    public OptionForm getOptionFormData(Long id) {
        Option entity = this.getById(id);
        return optionConverter.toForm(entity);
    }
    
    /**
     * 新增题目选项
     *
     * @param formData 题目选项表单对象
     * @return 是否新增成功
     */
    @Override
    public boolean saveOption(OptionForm formData) {
        Option entity = optionConverter.toEntity(formData);
        entity.setCreateBy(SecurityUtils.getUserId());
        entity.setUpdateBy(SecurityUtils.getUserId());
        return this.save(entity);
    }
    
    /**
     * 更新题目选项
     *
     * @param id   题目选项ID
     * @param formData 题目选项表单对象
     * @return 是否修改成功
     */
    @Override
    public boolean updateOption(Long id,OptionForm formData) {
        Option entity = optionConverter.toEntity(formData);
        entity.setCreateBy(SecurityUtils.getUserId());
        entity.setUpdateBy(SecurityUtils.getUserId());
        return this.updateById(entity);
    }
    
    /**
     * 删除题目选项
     *
     * @param ids 题目选项ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    @Override
    public boolean deleteOptions(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的题目选项数据为空");
        // 逻辑删除
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .toList();
        return this.removeByIds(idList);
    }

    @Override
    public List<OptionVO> listByQuestionId(Long id) {

        return this.baseMapper.getOptions(id);
    }

}
