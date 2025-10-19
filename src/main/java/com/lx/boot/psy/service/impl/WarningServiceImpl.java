package com.lx.boot.psy.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.boot.psy.mapper.WarningMapper;
import com.lx.boot.psy.service.WarningService;
import com.lx.boot.psy.model.entity.Warning;
import com.lx.boot.psy.model.form.WarningForm;
import com.lx.boot.psy.model.query.WarningQuery;
import com.lx.boot.psy.model.vo.WarningVO;
import com.lx.boot.psy.converter.WarningConverter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;

/**
 * 测评预警记录服务实现类
 *
 * @author liuh
 * @since 2025-10-19 18:04
 */
@Service
@RequiredArgsConstructor
public class WarningServiceImpl extends ServiceImpl<WarningMapper, Warning> implements WarningService {

    private final WarningConverter warningConverter;

    /**
    * 获取测评预警记录分页列表
    *
    * @param queryParams 查询参数
    * @return {@link IPage<WarningVO>} 测评预警记录分页列表
    */
    @Override
    public IPage<WarningVO> getWarningPage(WarningQuery queryParams) {
        Page<WarningVO> pageVO = this.baseMapper.getWarningPage(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams
        );
        return pageVO;
    }
    
    /**
     * 获取测评预警记录表单数据
     *
     * @param id 测评预警记录ID
     * @return 测评预警记录表单数据
     */
    @Override
    public WarningForm getWarningFormData(Long id) {
        Warning entity = this.getById(id);
        return warningConverter.toForm(entity);
    }
    
    /**
     * 新增测评预警记录
     *
     * @param formData 测评预警记录表单对象
     * @return 是否新增成功
     */
    @Override
    public boolean saveWarning(WarningForm formData) {
        Warning entity = warningConverter.toEntity(formData);
        return this.save(entity);
    }
    
    /**
     * 更新测评预警记录
     *
     * @param id   测评预警记录ID
     * @param formData 测评预警记录表单对象
     * @return 是否修改成功
     */
    @Override
    public boolean updateWarning(Long id,WarningForm formData) {
        Warning entity = warningConverter.toEntity(formData);
        return this.updateById(entity);
    }
    
    /**
     * 删除测评预警记录
     *
     * @param ids 测评预警记录ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    @Override
    public boolean deleteWarnings(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的测评预警记录数据为空");
        // 逻辑删除
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .toList();
        return this.removeByIds(idList);
    }

}
