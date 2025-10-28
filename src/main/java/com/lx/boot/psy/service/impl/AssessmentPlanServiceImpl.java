package com.lx.boot.psy.service.impl;

import com.lx.boot.core.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.boot.psy.mapper.AssessmentPlanMapper;
import com.lx.boot.psy.service.AssessmentPlanService;
import com.lx.boot.psy.model.entity.AssessmentPlan;
import com.lx.boot.psy.model.form.AssessmentPlanForm;
import com.lx.boot.psy.model.query.AssessmentPlanQuery;
import com.lx.boot.psy.model.vo.AssessmentPlanVO;
import com.lx.boot.psy.converter.AssessmentPlanConverter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;

/**
 * 测评计划服务实现类
 *
 * @author liuh
 * @since 2025-10-19 18:03
 */
@Service
@RequiredArgsConstructor
public class AssessmentPlanServiceImpl extends ServiceImpl<AssessmentPlanMapper, AssessmentPlan> implements AssessmentPlanService {

    private final AssessmentPlanConverter assessmentPlanConverter;

    /**
    * 获取测评计划分页列表
    *
    * @param queryParams 查询参数
    * @return {@link IPage<AssessmentPlanVO>} 测评计划分页列表
    */
    @Override
    public IPage<AssessmentPlanVO> getAssessmentPlanPage(AssessmentPlanQuery queryParams) {
        Page<AssessmentPlanVO> pageVO = this.baseMapper.getAssessmentPlanPage(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams
        );
        return pageVO;
    }
    
    /**
     * 获取测评计划表单数据
     *
     * @param id 测评计划ID
     * @return 测评计划表单数据
     */
    @Override
    public AssessmentPlanForm getAssessmentPlanFormData(Long id) {
        AssessmentPlan entity = this.getById(id);
        return assessmentPlanConverter.toForm(entity);
    }
    
    /**
     * 新增测评计划
     *
     * @param formData 测评计划表单对象
     * @return 是否新增成功
     */
    @Override
    public boolean saveAssessmentPlan(AssessmentPlanForm formData) {
        AssessmentPlan entity = assessmentPlanConverter.toEntity(formData);
        entity.setCreateBy(SecurityUtils.getUserId());
        entity.setUpdateBy(SecurityUtils.getUserId());
        return this.save(entity);
    }
    
    /**
     * 更新测评计划
     *
     * @param id   测评计划ID
     * @param formData 测评计划表单对象
     * @return 是否修改成功
     */
    @Override
    public boolean updateAssessmentPlan(Long id,AssessmentPlanForm formData) {
        AssessmentPlan entity = assessmentPlanConverter.toEntity(formData);
        return this.updateById(entity);
    }
    
    /**
     * 删除测评计划
     *
     * @param ids 测评计划ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    @Override
    public boolean deleteAssessmentPlans(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的测评计划数据为空");
        // 逻辑删除
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .toList();
        return this.removeByIds(idList);
    }

}
