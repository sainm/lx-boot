package com.lx.boot.psy.service;

import com.lx.boot.psy.model.entity.NormScoreDistribution;
import com.lx.boot.psy.model.form.NormScoreDistributionForm;
import com.lx.boot.psy.model.query.NormScoreDistributionQuery;
import com.lx.boot.psy.model.vo.NormScoreDistributionVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 常模分数分布服务类
 *
 * @author liuh
 * @since 2025-10-28 13:04
 */
public interface NormScoreDistributionService extends IService<NormScoreDistribution> {

    /**
     *常模分数分布分页列表
     *
     * @return {@link IPage<NormScoreDistributionVO>} 常模分数分布分页列表
     */
    IPage<NormScoreDistributionVO> getNormScoreDistributionPage(NormScoreDistributionQuery queryParams);

    /**
     * 获取常模分数分布表单数据
     *
     * @param id 常模分数分布ID
     * @return 常模分数分布表单数据
     */
     NormScoreDistributionForm getNormScoreDistributionFormData(Long id);

    /**
     * 新增常模分数分布
     *
     * @param formData 常模分数分布表单对象
     * @return 是否新增成功
     */
    boolean saveNormScoreDistribution(NormScoreDistributionForm formData);

    /**
     * 修改常模分数分布
     *
     * @param id   常模分数分布ID
     * @param formData 常模分数分布表单对象
     * @return 是否修改成功
     */
    boolean updateNormScoreDistribution(Long id, NormScoreDistributionForm formData);

    /**
     * 删除常模分数分布
     *
     * @param ids 常模分数分布ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    boolean deleteNormScoreDistributions(String ids);

}
