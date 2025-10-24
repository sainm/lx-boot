package com.lx.boot.psy.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.boot.psy.mapper.ScaleVersionMapper;
import com.lx.boot.psy.service.ScaleVersionService;
import com.lx.boot.psy.model.entity.ScaleVersion;
import com.lx.boot.psy.model.form.ScaleVersionForm;
import com.lx.boot.psy.model.query.ScaleVersionQuery;
import com.lx.boot.psy.model.vo.ScaleVersionVO;
import com.lx.boot.psy.converter.ScaleVersionConverter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;

/**
 * 量版本服务实现类
 *
 * @author liuh
 * @since 2025-10-24 12:15
 */
@Service
@RequiredArgsConstructor
public class ScaleVersionServiceImpl extends ServiceImpl<ScaleVersionMapper, ScaleVersion> implements ScaleVersionService {

    private final ScaleVersionConverter scaleVersionConverter;

    /**
    * 获取量版本分页列表
    *
    * @param queryParams 查询参数
    * @return {@link IPage<ScaleVersionVO>} 量版本分页列表
    */
    @Override
    public IPage<ScaleVersionVO> getScaleVersionPage(ScaleVersionQuery queryParams) {
        Page<ScaleVersionVO> pageVO = this.baseMapper.getScaleVersionPage(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams
        );
        return pageVO;
    }
    
    /**
     * 获取量版本表单数据
     *
     * @param id 量版本ID
     * @return 量版本表单数据
     */
    @Override
    public ScaleVersionForm getScaleVersionFormData(Long id) {
        ScaleVersion entity = this.getById(id);
        return scaleVersionConverter.toForm(entity);
    }
    
    /**
     * 新增量版本
     *
     * @param formData 量版本表单对象
     * @return 是否新增成功
     */
    @Override
    public boolean saveScaleVersion(ScaleVersionForm formData) {
        ScaleVersion entity = scaleVersionConverter.toEntity(formData);
        return this.save(entity);
    }
    
    /**
     * 更新量版本
     *
     * @param id   量版本ID
     * @param formData 量版本表单对象
     * @return 是否修改成功
     */
    @Override
    public boolean updateScaleVersion(Long id,ScaleVersionForm formData) {
        ScaleVersion entity = scaleVersionConverter.toEntity(formData);
        return this.updateById(entity);
    }
    
    /**
     * 删除量版本
     *
     * @param ids 量版本ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    @Override
    public boolean deleteScaleVersions(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的量版本数据为空");
        // 逻辑删除
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .toList();
        return this.removeByIds(idList);
    }

}
