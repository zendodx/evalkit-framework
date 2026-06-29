package com.evalkit.framework.eval.node.data_generator;


import com.evalkit.framework.common.thread.BatchRunner;
import com.evalkit.framework.common.thread.PoolName;
import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.common.utils.llm.LLMUtils;
import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.common.utils.nlp.NLPUtils;
import com.evalkit.framework.common.utils.random.UuidUtils;
import com.evalkit.framework.eval.node.data_generator.config.KGBasedQueryGeneratorConfig;
import com.evalkit.framework.eval.node.data_generator.filter.SimilarityFilter;
import com.evalkit.framework.eval.node.data_generator.kg.JenaKnowledgeExtractor;
import com.evalkit.framework.eval.node.data_generator.model.ScenarioConfig;
import com.evalkit.framework.eval.node.data_generator.model.TestCase;
import com.evalkit.framework.eval.node.data_generator.model.Turn;
import com.evalkit.framework.eval.node.data_generator.prompt.PromptEngine;
import com.evalkit.framework.infra.service.llm.LLMService;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.apache.jena.atlas.lib.RandomLib.random;

/**
 * 基于知识图谱的Query生成器
 */
@Slf4j
public class KGBasedQueryGenerator extends DataGenerator {
    protected KGBasedQueryGeneratorConfig config;
    // 生成用例计数器
    protected AtomicInteger caseCounter = new AtomicInteger(1);

    public KGBasedQueryGenerator(KGBasedQueryGeneratorConfig config) {
        super(config);
        this.config = config;
    }

    @Data
    @AllArgsConstructor
    protected static class ScenarioConfigAndSession {
        String scenarioConfigFilePath;
        String sessionId;
    }

    /**
     * 并发生成评测数据
     */
    @Override
    protected List<Map<String, Object>> generate() throws Exception {
        List<Map<String, Object>> res = new ArrayList<>();

        List<ScenarioConfigAndSession> scenarioConfigAndSessions = new ArrayList<>();
        List<String> scenarioConfigFilePaths = config.getScenarioConfigFilePath();

        // 遍历配置文件列表
        for (String scenarioConfigFilePath : scenarioConfigFilePaths) {
            // 每个配置生成指定条数会话
            Integer generateCount = config.getGenerateCount();
            for (int i = 0; i < generateCount; i++) {
                scenarioConfigAndSessions.add(new ScenarioConfigAndSession(scenarioConfigFilePath, UuidUtils.generateUuid()));
            }
        }

        // 并发生成单会话多轮Query
        List<List<Map<String, Object>>> rawQueries = BatchRunner.runBatch(scenarioConfigAndSessions, this::generateSessionQueries,
                PoolName.DATA_GENERATOR, config.getThreadNum(), size -> size * config.getBatchTimeoutSec());
        if (CollectionUtils.isEmpty(rawQueries)) {
            throw new IllegalArgumentException("[KGBasedQueryGenerator] Generate eval case data failed");
        }

        // 合并结果
        for (List<Map<String, Object>> queryList : rawQueries) {
            res.addAll(queryList);
        }
        return res;
    }


    /**
     * 生成单个会话的测试用例 (主流程编排)
     */
    protected List<Map<String, Object>> generateSessionQueries(ScenarioConfigAndSession scenarioConfigAndSession) {
        String sessionId = scenarioConfigAndSession.sessionId;
        String scenarioConfigFilePath = scenarioConfigAndSession.scenarioConfigFilePath;

        log.debug("[KGBasedQueryGenerator] Start generating queries for sessionId: {}", scenarioConfigAndSession.getSessionId());

        try {
            // 加载并解析场景配置
            ScenarioConfig scenarioConfig = loadAndParseConfig(scenarioConfigFilePath);
            if (scenarioConfig == null) return Collections.emptyList();

            // 从知识图谱抽取并随机采样一条合法数据
            Map<String, String> targetKgData = extractAndSampleKgData(scenarioConfig.sparqlTemplate);
            if (targetKgData == null) return Collections.emptyList();

            // 调用大模型生成对话
            List<Turn> generatedQueries = generateDialogueFromLlm(scenarioConfig, targetKgData);
            if (CollectionUtils.isEmpty(generatedQueries)) return Collections.emptyList();

            // 向量相似度校验
            if (!evaluateSimilarity(scenarioConfig, generatedQueries)) {
                return Collections.emptyList(); // 相似度不达标，直接丢弃
            }

            // 组装测试用例 (动态注入断言)
            TestCase testCase = assembleTestCase(scenarioConfig, targetKgData, generatedQueries, sessionId);

            // 格式化输出
            return formatOutput(Collections.singletonList(testCase));
        } catch (Exception e) {
            // 顶层兜底捕获，防止单个会话生成失败导致整个评测任务崩溃
            log.error("[KGBasedQueryGenerator] Unhandled exception during query generation for sessionId: {}", sessionId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 加载并解析场景配置
     */
    private ScenarioConfig loadAndParseConfig(String scenarioConfigFilePath) {
        try {
            String configJson = readFileContent(scenarioConfigFilePath);
            return JsonUtils.fromJson(configJson, ScenarioConfig.class);
        } catch (Exception e) {
            log.error("[KGBasedQueryGenerator] Failed to load or parse scenario config: {}", config.getScenarioConfigFilePath(), e);
            return null;
        }
    }

    /**
     * 抽取图谱数据并随机采样一条
     */
    private Map<String, String> extractAndSampleKgData(String sparqlTemplate) {
        try {
            String rdfData = readFileContent(config.getKgFilePath());
            JenaKnowledgeExtractor extractor = new JenaKnowledgeExtractor(rdfData);
            List<Map<String, String>> extractedDataList = extractor.extractData(sparqlTemplate);

            if (CollectionUtils.isEmpty(extractedDataList)) {
                log.warn("[KGBasedQueryGenerator] Extracted empty list from KG, please check SPARQL or KG data.");
                return null;
            }

            log.debug("[KGBasedQueryGenerator] Extracted {} KG data paths.", extractedDataList.size());
            // 随机抽取一条数据用于本次生成
            return extractedDataList.get(random.nextInt(extractedDataList.size()));
        } catch (Exception e) {
            log.error("[KGBasedQueryGenerator] Failed to extract data from Knowledge Graph.", e);
            return null;
        }
    }

    /**
     * 调用 LLM 并解析返回的 JSON
     */
    private List<Turn> generateDialogueFromLlm(ScenarioConfig scenarioConfig, Map<String, String> kgData) {
        try {
            PromptEngine promptEngine = new PromptEngine();
            String prompt = promptEngine.generatePrompt(scenarioConfig.goldenCase, kgData);

            LLMService llmService = config.getLlmService();
            String llmResponse = llmService.chat(prompt);
            log.debug("[KGBasedQueryGenerator] Call LLM success, response: {}", llmResponse);

            String cleanJson = LLMUtils.extractLLMJsonResponse(llmResponse);
            return JsonUtils.fromJson(cleanJson, new TypeReference<List<Turn>>() {
            });
        } catch (Exception e) {
            log.error("[KGBasedQueryGenerator] Failed to generate or parse dialogue from LLM.", e);
            return null;
        }
    }

    /**
     * 评估生成的对话与标杆用例的相似度
     */
    private boolean evaluateSimilarity(ScenarioConfig config, List<Turn> generatedQueries) {
        if (!Boolean.TRUE.equals(this.config.getEnableSimilarityFilter())) {
            // 未开启过滤，直接放行
            return true;
        }

        try {
            String goldenText = SimilarityFilter.combineDialogue(config.goldenCase.dialogue);
            String generatedText = SimilarityFilter.combineDialogue(generatedQueries);

            double similarityScore = NLPUtils.cosineSimilarity(generatedText, goldenText);
            boolean passed = similarityScore >= config.minSimilarity && similarityScore <= config.maxSimilarity;

            if (passed) {
                log.debug("[KGBasedQueryGenerator] Similarity check passed ({}).", similarityScore);
            } else {
                log.debug("[KGBasedQueryGenerator] Similarity check failed ({}), discarded.", similarityScore);
            }
            return passed;
        } catch (Exception e) {
            log.error("[KGBasedQueryGenerator] Exception during similarity calculation.", e);
            // 计算异常时，建议采取保守策略丢弃数据
            return false;
        }
    }

    /**
     * 组装 TestCase，动态绑定断言
     */
    private TestCase assembleTestCase(ScenarioConfig scenarioConfig, Map<String, String> kgData, List<Turn> generatedQueries, String sessionId) {
        TestCase testCase = new TestCase();
        testCase.testCaseId = config.getCaseIdPrefix() + String.format("%03d", caseCounter.getAndIncrement());
        testCase.scenarioId = scenarioConfig.scenarioId;
        testCase.kgSource = kgData;

        // 动态绑定图谱数据到断言中
        for (Turn generatedTurn : generatedQueries) {
            // sessionId 与 configTurn 是否匹配无关，所有轮次都必须注入
            generatedTurn.sessionId = sessionId;

            Turn configTurn = scenarioConfig.goldenCase.dialogue.stream()
                    .filter(t -> t.turn == generatedTurn.turn)
                    .findFirst()
                    .orElse(null);

            if (configTurn != null) {
                // 注入场景与意图（来自模板对应轮次的配置）
                generatedTurn.scenario = configTurn.scenario;
                generatedTurn.intent = configTurn.intent;
                generatedTurn.assertType = configTurn.assertType;

                if (configTurn.expectedVars != null) {
                    List<String> realKeywords = new ArrayList<>();
                    for (String varName : configTurn.expectedVars) {
                        if (kgData.containsKey(varName)) {
                            realKeywords.add(kgData.get(varName));
                        }
                    }
                    if (!realKeywords.isEmpty()) {
                        generatedTurn.expectedKeywords = realKeywords;
                    }
                }
            }
        }
        testCase.queries = generatedQueries;
        return testCase;
    }

    /**
     * 将 TestCase 格式化为评测框架需要的 Map 结构
     */
    private List<Map<String, Object>> formatOutput(List<TestCase> testCases) {
        List<Map<String, Object>> res = new ArrayList<>();
        Boolean enableOneRawOneSession = config.getEnableOneRawOneSession();

        for (TestCase testCase : testCases) {
            Map<String, Object> testCaseMap = MapUtils.beanToMap(testCase);
            if (Boolean.TRUE.equals(enableOneRawOneSession)) {
                // 一行表示一个完整的会话
                List<Map<String, Object>> queryMapList = new ArrayList<>();
                // 将query和turn字段名称转为指定名称
                for (Turn turn : testCase.getQueries()) {
                    queryMapList.add(convertTurnToMap(turn, config.getQueryFieldName(), config.getTurnFieldName(),
                            config.getSessionIdFieldName(), config.getScenarioFieldName(), config.getIntentFieldName()));
                }
                testCaseMap.put("queries", queryMapList);
                res.add(testCaseMap);
            } else {
                // 一行表示单轮 Query (拆平)
                List<Turn> queries = testCase.getQueries();
                // 移除嵌套的列表
                testCaseMap.remove("queries");
                for (Turn turn : queries) {
                    // 存入会话级公共数据
                    Map<String, Object> queryMap = new LinkedHashMap<>(testCaseMap);
                    // 存入单轮特有数据
                    Map<String, Object> turnMap = convertTurnToMap(turn, config.getQueryFieldName(),
                            config.getTurnFieldName(), config.getSessionIdFieldName(), config.getScenarioFieldName(),
                            config.getIntentFieldName());
                    queryMap.putAll(turnMap);
                    res.add(queryMap);
                }
            }
        }
        log.debug("[KGBasedQueryGenerator] Successfully formatted output, total rows: {}", res.size());
        return res;
    }

    protected Map<String, Object> convertTurnToMap(Turn turn, String queryFieldName, String turnFieldName,
                                                    String sessionIdFieldName, String scenarioFieldName, String intentFieldName) {
        Map<String, Object> turnMap = MapUtils.beanToMap(turn);
        // 删除原本的字段
        turnMap.remove("query");
        turnMap.remove("turn");
        turnMap.remove("sessionId");
        turnMap.remove("scenario");
        turnMap.remove("intent");
        // 添加指定名称的字段
        turnMap.put(queryFieldName, turn.query);
        turnMap.put(turnFieldName, turn.turn);
        turnMap.put(sessionIdFieldName, turn.sessionId);
        turnMap.put(scenarioFieldName, turn.scenario);
        turnMap.put(intentFieldName, turn.intent);
        return turnMap;
    }

    /**
     * 读取文件内容
     */
    private static String readFileContent(String filePath) {
        // 尝试作为普通文件系统路径读取 (绝对路径或相对路径)
        try {
            return new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // 如果文件系统找不到，尝试从 Classpath (如 src/main/resources) 读取
            try (InputStream is = KGBasedQueryGenerator.class.getClassLoader().getResourceAsStream(filePath)) {
                if (is == null) {
                    // 如果 Classpath 里也没有，抛出明确的异常
                    throw new RuntimeException("File not found! Please check the path is correct (file system and classpath both not found): " + filePath);
                }
                // 使用 Scanner 快速将 InputStream 转换为 String (Java 8 常用技巧)
                try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name())) {
                    scanner.useDelimiter("\\A");
                    return scanner.hasNext() ? scanner.next() : "";
                }
            } catch (IOException ex) {
                throw new RuntimeException("Read file stream error: " + filePath, ex);
            }
        }
    }
}
