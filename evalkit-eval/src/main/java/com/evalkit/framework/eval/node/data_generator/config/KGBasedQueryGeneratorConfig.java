package com.evalkit.framework.eval.node.data_generator.config;

import com.evalkit.framework.infra.service.llm.LLMService;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
public class KGBasedQueryGeneratorConfig extends DataGeneratorConfig {
    // 知识图谱文件路径
    protected String kgFilePath;
    // 场景配置文件路径
    protected List<String> scenarioConfigFilePath;
    // 大模型服务
    protected LLMService llmService;
    // 是否开启相似度过滤, 默认不开启
    @Builder.Default
    protected Boolean enableSimilarityFilter = false;
    // 生成条数
    @Builder.Default
    protected Integer generateCount = 1;
    // 是否开启每一行表示一个会话, 开启则每行表示一个会话包含多轮Query, 不开启则每行表示一个Query,多行用sessionId区分会话
    // 默认不开启
    @Builder.Default
    protected Boolean enableOneRawOneSession = false;
    // 生成用例Id前缀
    @Builder.Default
    protected String caseIdPrefix = "gen_case_";
    // 会话id字段名称
    @Builder.Default
    protected String sessionIdFieldName = "sessionId";
    // 轮次字段名称
    @Builder.Default
    protected String turnFieldName = "turn";
    // query字段名称
    @Builder.Default
    protected String queryFieldName = "query";
    // 场景字段名称
    @Builder.Default
    protected String scenarioFieldName = "scenario";
    // 意图字段名称
    @Builder.Default
    protected String intentFieldName = "intent";
}
