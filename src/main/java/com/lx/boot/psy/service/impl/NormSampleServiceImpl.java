package com.lx.boot.psy.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.boot.psy.mapper.NormSampleMapper;
import com.lx.boot.psy.service.NormSampleService;
import com.lx.boot.psy.model.entity.NormSample;
import com.lx.boot.psy.model.form.NormSampleForm;
import com.lx.boot.psy.model.query.NormSampleQuery;
import com.lx.boot.psy.model.vo.NormSampleVO;
import com.lx.boot.psy.converter.NormSampleConverter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;

/**
 * 量常模样本定义服务实现类
 *
 * @author liuh
 * @since 2025-10-28 12:27
 */
@Service
@RequiredArgsConstructor
public class NormSampleServiceImpl extends ServiceImpl<NormSampleMapper, NormSample> implements NormSampleService {

    private final NormSampleConverter normSampleConverter;

    /**
    * 获取量常模样本定义分页列表
    *
    * @param queryParams 查询参数
    * @return {@link IPage<NormSampleVO>} 量常模样本定义分页列表
    */
    @Override
    public IPage<NormSampleVO> getNormSamplePage(NormSampleQuery queryParams) {
        Page<NormSampleVO> pageVO = this.baseMapper.getNormSamplePage(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams
        );
        return pageVO;
    }
    
    /**
     * 获取量常模样本定义表单数据
     *
     * @param id 量常模样本定义ID
     * @return 量常模样本定义表单数据
     */
    @Override
    public NormSampleForm getNormSampleFormData(Long id) {
        NormSample entity = this.getById(id);
        return normSampleConverter.toForm(entity);
    }
    
    /**
     * 新增量常模样本定义
     *
     * @param formData 量常模样本定义表单对象
     * @return 是否新增成功
     */
    @Override
    public boolean saveNormSample(NormSampleForm formData) {
        NormSample entity = normSampleConverter.toEntity(formData);
        return this.save(entity);
    }
    
    /**
     * 更新量常模样本定义
     *
     * @param id   量常模样本定义ID
     * @param formData 量常模样本定义表单对象
     * @return 是否修改成功
     */
    @Override
    public boolean updateNormSample(Long id,NormSampleForm formData) {
        NormSample entity = normSampleConverter.toEntity(formData);
        return this.updateById(entity);
    }
    
    /**
     * 删除量常模样本定义
     *
     * @param ids 量常模样本定义ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    @Override
    public boolean deleteNormSamples(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的量常模样本定义数据为空");
        // 逻辑删除
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .toList();
        return this.removeByIds(idList);
    }

}
