package com.evalkit.framework.eval.node.api.config;

import com.evalkit.framework.eval.model.DataItem;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Comparator;

@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
public class OrderedApiCompletionConfig extends ApiCompletionConfig {
    /* 同组内元素排序 */
    @Builder.Default
    protected Comparator<DataItem> comparator = null;
}
