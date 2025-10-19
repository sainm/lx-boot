package com.lx.boot.system.converter;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lx.boot.system.model.entity.DictItem;
import com.lx.boot.system.model.form.DictItemForm;
import com.lx.boot.system.model.vo.DictPageVO;
import com.lx.boot.common.model.Option;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 字典项对象转换器
 *
 * @author Ray.Hao
 * @since 2022/6/8
 */
@Mapper(componentModel = "spring")
public interface DictItemConverter {

    Page<DictPageVO> toPageVo(Page<DictItem> page);

    DictItemForm toForm(DictItem entity);

    DictItem toEntity(DictItemForm formFata);

    Option<Long> toOption(DictItem dictItem);
    List<Option<Long>> toOption(List<DictItem> dictData);
}
