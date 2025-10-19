package com.lx.boot.psy.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.boot.psy.mapper.ScaleMapper;
import com.lx.boot.psy.service.ScaleService;
import com.lx.boot.psy.model.entity.Scale;
import com.lx.boot.psy.model.form.ScaleForm;
import com.lx.boot.psy.model.query.ScaleQuery;
import com.lx.boot.psy.model.vo.ScaleVO;
import com.lx.boot.psy.converter.ScaleConverter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;

/**
 * 量主服务实现类
 *
 * @author liuh
 * @since 2025-10-19 18:02
 */
@Service
@RequiredArgsConstructor
public class ScaleServiceImpl extends ServiceImpl<ScaleMapper, Scale> implements ScaleService {

    private final ScaleConverter scaleConverter;

    /**
    * 获取量主分页列表
    *
    * @param queryParams 查询参数
    * @return {@link IPage<ScaleVO>} 量主分页列表
    */
    @Override
    public IPage<ScaleVO> getScalePage(ScaleQuery queryParams) {
        Page<ScaleVO> pageVO = this.baseMapper.getScalePage(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams
        );
        return pageVO;
    }
    
    /**
     * 获取量主表单数据
     *
     * @param id 量主ID
     * @return 量主表单数据
     */
    @Override
    public ScaleForm getScaleFormData(Long id) {
        Scale entity = this.getById(id);
        return scaleConverter.toForm(entity);
    }
    
    /**
     * 新增量主
     *
     * @param formData 量主表单对象
     * @return 是否新增成功
     */
    @Override
    public boolean saveScale(ScaleForm formData) {
        Scale entity = scaleConverter.toEntity(formData);
        return this.save(entity);
    }
    
    /**
     * 更新量主
     *
     * @param id   量主ID
     * @param formData 量主表单对象
     * @return 是否修改成功
     */
    @Override
    public boolean updateScale(Long id,ScaleForm formData) {
        Scale entity = scaleConverter.toEntity(formData);
        return this.updateById(entity);
    }
    
    /**
     * 删除量主
     *
     * @param ids 量主ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    @Override
    public boolean deleteScales(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的量主数据为空");
        // 逻辑删除
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .toList();
        return this.removeByIds(idList);
    }

}
