package com.evalkit.framework.eval.node.api_wrapper.config;

import com.evalkit.framework.workflow.model.NodeConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * api调用结果装饰器配置
 */
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@Data
public class ApiCompletionWrapperConfig extends NodeConfig {
}
