package com.lx.boot.psy.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.boot.psy.mapper.AssessmentRecordMapper;
import com.lx.boot.psy.service.AssessmentRecordService;
import com.lx.boot.psy.model.entity.AssessmentRecord;
import com.lx.boot.psy.model.form.AssessmentRecordForm;
import com.lx.boot.psy.model.query.AssessmentRecordQuery;
import com.lx.boot.psy.model.vo.AssessmentRecordVO;
import com.lx.boot.psy.converter.AssessmentRecordConverter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;

/**
 * 测评记录服务实现类
 *
 * @author liuh
 * @since 2025-10-19 18:06
 */
@Service
@RequiredArgsConstructor
public class AssessmentRecordServiceImpl extends ServiceImpl<AssessmentRecordMapper, AssessmentRecord> implements AssessmentRecordService {

    private final AssessmentRecordConverter assessmentRecordConverter;

    /**
    * 获取测评记录分页列表
    *
    * @param queryParams 查询参数
    * @return {@link IPage<AssessmentRecordVO>} 测评记录分页列表
    */
    @Override
    public IPage<AssessmentRecordVO> getAssessmentRecordPage(AssessmentRecordQuery queryParams) {
        Page<AssessmentRecordVO> pageVO = this.baseMapper.getAssessmentRecordPage(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams
        );
        return pageVO;
    }
    
    /**
     * 获取测评记录表单数据
     *
     * @param id 测评记录ID
     * @return 测评记录表单数据
     */
    @Override
    public AssessmentRecordForm getAssessmentRecordFormData(Long id) {
        AssessmentRecord entity = this.getById(id);
        return assessmentRecordConverter.toForm(entity);
    }
    
    /**
     * 新增测评记录
     *
     * @param formData 测评记录表单对象
     * @return 是否新增成功
     */
    @Override
    public boolean saveAssessmentRecord(AssessmentRecordForm formData) {
        AssessmentRecord entity = assessmentRecordConverter.toEntity(formData);
        return this.save(entity);
    }
    
    /**
     * 更新测评记录
     *
     * @param id   测评记录ID
     * @param formData 测评记录表单对象
     * @return 是否修改成功
     */
    @Override
    public boolean updateAssessmentRecord(Long id,AssessmentRecordForm formData) {
        AssessmentRecord entity = assessmentRecordConverter.toEntity(formData);
        return this.updateById(entity);
    }
    
    /**
     * 删除测评记录
     *
     * @param ids 测评记录ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    @Override
    public boolean deleteAssessmentRecords(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的测评记录数据为空");
        // 逻辑删除
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .toList();
        return this.removeByIds(idList);
    }

}
