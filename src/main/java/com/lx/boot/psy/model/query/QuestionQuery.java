package com.lx.boot.psy.model.query;

import com.lx.boot.common.base.BasePageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 题目分页查询对象
 *
 * @author liuh
 * @since 2025-10-19 18:01
 */
@Schema(description ="题目查询对象")
@Getter
@Setter
public class QuestionQuery extends BasePageQuery {

}
