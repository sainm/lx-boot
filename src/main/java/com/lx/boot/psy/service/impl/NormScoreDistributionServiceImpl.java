package com.lx.boot.psy.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.boot.psy.mapper.NormScoreDistributionMapper;
import com.lx.boot.psy.service.NormScoreDistributionService;
import com.lx.boot.psy.model.entity.NormScoreDistribution;
import com.lx.boot.psy.model.form.NormScoreDistributionForm;
import com.lx.boot.psy.model.query.NormScoreDistributionQuery;
import com.lx.boot.psy.model.vo.NormScoreDistributionVO;
import com.lx.boot.psy.converter.NormScoreDistributionConverter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;

/**
 * 常模分数分布服务实现类
 *
 * @author liuh
 * @since 2025-10-28 13:04
 */
@Service
@RequiredArgsConstructor
public class NormScoreDistributionServiceImpl extends ServiceImpl<NormScoreDistributionMapper, NormScoreDistribution> implements NormScoreDistributionService {

    private final NormScoreDistributionConverter normScoreDistributionConverter;

    /**
    * 获取常模分数分布分页列表
    *
    * @param queryParams 查询参数
    * @return {@link IPage<NormScoreDistributionVO>} 常模分数分布分页列表
    */
    @Override
    public IPage<NormScoreDistributionVO> getNormScoreDistributionPage(NormScoreDistributionQuery queryParams) {
        Page<NormScoreDistributionVO> pageVO = this.baseMapper.getNormScoreDistributionPage(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams
        );
        return pageVO;
    }
    
    /**
     * 获取常模分数分布表单数据
     *
     * @param id 常模分数分布ID
     * @return 常模分数分布表单数据
     */
    @Override
    public NormScoreDistributionForm getNormScoreDistributionFormData(Long id) {
        NormScoreDistribution entity = this.getById(id);
        return normScoreDistributionConverter.toForm(entity);
    }
    
    /**
     * 新增常模分数分布
     *
     * @param formData 常模分数分布表单对象
     * @return 是否新增成功
     */
    @Override
    public boolean saveNormScoreDistribution(NormScoreDistributionForm formData) {
        NormScoreDistribution entity = normScoreDistributionConverter.toEntity(formData);
        return this.save(entity);
    }
    
    /**
     * 更新常模分数分布
     *
     * @param id   常模分数分布ID
     * @param formData 常模分数分布表单对象
     * @return 是否修改成功
     */
    @Override
    public boolean updateNormScoreDistribution(Long id,NormScoreDistributionForm formData) {
        NormScoreDistribution entity = normScoreDistributionConverter.toEntity(formData);
        return this.updateById(entity);
    }
    
    /**
     * 删除常模分数分布
     *
     * @param ids 常模分数分布ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    @Override
    public boolean deleteNormScoreDistributions(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的常模分数分布数据为空");
        // 逻辑删除
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .toList();
        return this.removeByIds(idList);
    }

}
