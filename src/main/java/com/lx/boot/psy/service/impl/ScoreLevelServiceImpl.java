package com.lx.boot.psy.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lx.boot.psy.mapper.ScoreLevelMapper;
import com.lx.boot.psy.service.ScoreLevelService;
import com.lx.boot.psy.model.entity.ScoreLevel;
import com.lx.boot.psy.model.form.ScoreLevelForm;
import com.lx.boot.psy.model.query.ScoreLevelQuery;
import com.lx.boot.psy.model.vo.ScoreLevelVO;
import com.lx.boot.psy.converter.ScoreLevelConverter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;

/**
 * 分数区间对应等级描述服务实现类
 *
 * @author liuh
 * @since 2025-10-24 14:01
 */
@Service
@RequiredArgsConstructor
public class ScoreLevelServiceImpl extends ServiceImpl<ScoreLevelMapper, ScoreLevel> implements ScoreLevelService {

    private final ScoreLevelConverter scoreLevelConverter;

    /**
    * 获取分数区间对应等级描述分页列表
    *
    * @param queryParams 查询参数
    * @return {@link IPage<ScoreLevelVO>} 分数区间对应等级描述分页列表
    */
    @Override
    public IPage<ScoreLevelVO> getScoreLevelPage(ScoreLevelQuery queryParams) {
        Page<ScoreLevelVO> pageVO = this.baseMapper.getScoreLevelPage(
                new Page<>(queryParams.getPageNum(), queryParams.getPageSize()),
                queryParams
        );
        return pageVO;
    }
    
    /**
     * 获取分数区间对应等级描述表单数据
     *
     * @param id 分数区间对应等级描述ID
     * @return 分数区间对应等级描述表单数据
     */
    @Override
    public ScoreLevelForm getScoreLevelFormData(Long id) {
        ScoreLevel entity = this.getById(id);
        return scoreLevelConverter.toForm(entity);
    }
    
    /**
     * 新增分数区间对应等级描述
     *
     * @param formData 分数区间对应等级描述表单对象
     * @return 是否新增成功
     */
    @Override
    public boolean saveScoreLevel(ScoreLevelForm formData) {
        ScoreLevel entity = scoreLevelConverter.toEntity(formData);
        return this.save(entity);
    }
    
    /**
     * 更新分数区间对应等级描述
     *
     * @param id   分数区间对应等级描述ID
     * @param formData 分数区间对应等级描述表单对象
     * @return 是否修改成功
     */
    @Override
    public boolean updateScoreLevel(Long id,ScoreLevelForm formData) {
        ScoreLevel entity = scoreLevelConverter.toEntity(formData);
        return this.updateById(entity);
    }
    
    /**
     * 删除分数区间对应等级描述
     *
     * @param ids 分数区间对应等级描述ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    @Override
    public boolean deleteScoreLevels(String ids) {
        Assert.isTrue(StrUtil.isNotBlank(ids), "删除的分数区间对应等级描述数据为空");
        // 逻辑删除
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(Long::parseLong)
                .toList();
        return this.removeByIds(idList);
    }

}
