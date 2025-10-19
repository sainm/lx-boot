package com.lx.boot.psy.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.boot.psy.mapper.AssessmentAssignmentMapper;
import com.lx.boot.psy.service.AssessmentAssignmentService;
import com.lx.boot.psy.model.entity.AssessmentAssignment;
import com.lx.boot.psy.model.form.AssessmentAssignmentForm;
import com.lx.boot.psy.model.query.AssessmentAssignmentQuery;
import com.lx.boot.psy.model.vo.AssessmentAssignmentVO;
import com.lx.boot.psy.converter.AssessmentAssignmentConverter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;

/**
 * 测评任务分配服务实现类
 *
 * @author liuh
 * @since 2025-10-19 18:05
 */
@Service
@RequiredArgsConstructor
public class AssessmentAssignmentServiceImpl extends ServiceImpl<AssessmentAssignmentMapper, AssessmentAssignment> implements AssessmentAssignmentService {

    private final AssessmentAssignmentConverter assessmentAssignmentConverter;

    /**
    * 获取测评任务分配分页列表
    *
    * @param queryParams 查询参数
    * @return {@link IPage<AssessmentAssignmentVO>} 测评任务分配分页列表
    */
    @Override
    public IPage<AssessmentAssignmentVO> getAssessmentAssignmentPage(AssessmentAssignmentQuery queryParams) {
        Page<AssessmentAssignmentVO> pageVO = this.baseMapper.getAssessmentAssignmentPage(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams
        );
        return pageVO;
    }
    
    /**
     * 获取测评任务分配表单数据
     *
     * @param id 测评任务分配ID
     * @return 测评任务分配表单数据
     */
    @Override
    public AssessmentAssignmentForm getAssessmentAssignmentFormData(Long id) {
        AssessmentAssignment entity = this.getById(id);
        return assessmentAssignmentConverter.toForm(entity);
    }
    
    /**
     * 新增测评任务分配
     *
     * @param formData 测评任务分配表单对象
     * @return 是否新增成功
     */
    @Override
    public boolean saveAssessmentAssignment(AssessmentAssignmentForm formData) {
        AssessmentAssignment entity = assessmentAssignmentConverter.toEntity(formData);
        return this.save(entity);
    }
    
    /**
     * 更新测评任务分配
     *
     * @param id   测评任务分配ID
     * @param formData 测评任务分配表单对象
     * @return 是否修改成功
     */
    @Override
    public boolean updateAssessmentAssignment(Long id,AssessmentAssignmentForm formData) {
        AssessmentAssignment entity = assessmentAssignmentConverter.toEntity(formData);
        return this.updateById(entity);
    }
    
    /**
     * 删除测评任务分配
     *
     * @param ids 测评任务分配ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    @Override
    public boolean deleteAssessmentAssignments(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的测评任务分配数据为空");
        // 逻辑删除
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .toList();
        return this.removeByIds(idList);
    }

}
