package com.lx.boot.psy.service;

import com.lx.boot.psy.model.entity.ScoreLevel;
import com.lx.boot.psy.model.form.ScoreLevelForm;
import com.lx.boot.psy.model.query.ScoreLevelQuery;
import com.lx.boot.psy.model.vo.ScoreLevelVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 分数区间对应等级描述服务类
 *
 * @author liuh
 * @since 2025-10-19 17:58
 */
public interface ScoreLevelService extends IService<ScoreLevel> {

    /**
     *分数区间对应等级描述分页列表
     *
     * @return {@link IPage<ScoreLevelVO>} 分数区间对应等级描述分页列表
     */
    IPage<ScoreLevelVO> getScoreLevelPage(ScoreLevelQuery queryParams);

    /**
     * 获取分数区间对应等级描述表单数据
     *
     * @param id 分数区间对应等级描述ID
     * @return 分数区间对应等级描述表单数据
     */
     ScoreLevelForm getScoreLevelFormData(Long id);

    /**
     * 新增分数区间对应等级描述
     *
     * @param formData 分数区间对应等级描述表单对象
     * @return 是否新增成功
     */
    boolean saveScoreLevel(ScoreLevelForm formData);

    /**
     * 修改分数区间对应等级描述
     *
     * @param id   分数区间对应等级描述ID
     * @param formData 分数区间对应等级描述表单对象
     * @return 是否修改成功
     */
    boolean updateScoreLevel(Long id, ScoreLevelForm formData);

    /**
     * 删除分数区间对应等级描述
     *
     * @param ids 分数区间对应等级描述ID，多个以英文逗号(,)分割
     * @return 是否删除成功
     */
    boolean deleteScoreLevels(String ids);

}
