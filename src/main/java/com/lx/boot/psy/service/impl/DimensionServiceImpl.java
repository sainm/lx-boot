package com.lx.boot.psy.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.boot.psy.mapper.DimensionMapper;
import com.lx.boot.psy.service.DimensionService;
import com.lx.boot.psy.model.entity.Dimension;
import com.lx.boot.psy.model.form.DimensionForm;
import com.lx.boot.psy.model.query.DimensionQuery;
import com.lx.boot.psy.model.vo.DimensionVO;
import com.lx.boot.psy.converter.DimensionConverter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;

/**
 * 维度服务实现类
 *
 * @author liuh
 * @since 2025-10-19 18:01
 */
@Service
@RequiredArgsConstructor
public class DimensionServiceImpl extends ServiceImpl<DimensionMapper, Dimension> implements DimensionService {

    private final DimensionConverter dimensionConverter;

    /**
    * 获取维度分页列表
    *
    * @param queryParams 查询参数
    * @return {@link IPage<DimensionVO>} 维度分页列表
    */
    @Override
    public IPage<DimensionVO> getDimensionPage(DimensionQuery queryParams) {
        Page<DimensionVO> pageVO = this.baseMapper.getDimensionPage(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams
        );
        return pageVO;
    }
    
    /**
     * 获取维度表单数据
     *
     * @param id 维度ID
     * @return 维度表单数据
     */
    @Override
    public DimensionForm getDimensionFormData(Long id) {
        Dimension entity = this.getById(id);
        return dimensionConverter.toForm(entity);
    }
    
    /**
     * 新增维度
     *
     * @param formData 维度表单对象
     * @return 是否新增成功
     */
    @Override
    public boolean saveDimension(DimensionForm formData) {
        Dimension entity = dimensionConverter.toEntity(formData);
        return this.save(entity);
    }
    
    /**
     * 更新维度
     *
     * @param id   维度ID
     * @param formData 维度表单对象
     * @return 是否修改成功
     */
    @Override
    public boolean updateDimension(Long id,DimensionForm formData) {
        Dimension entity = dimensionConverter.toEntity(formData);
        return this.updateById(entity);
    }
    
    /**
     * 删除维度
     *
     * @param ids 维度ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    @Override
    public boolean deleteDimensions(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的维度数据为空");
        // 逻辑删除
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .toList();
        return this.removeByIds(idList);
    }

}
