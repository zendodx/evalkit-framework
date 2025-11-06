package com.evalkit.framework.eval.node.dataloader.datagen;

import com.evalkit.framework.common.thread.BatchRunner;
import com.evalkit.framework.common.thread.PoolName;
import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.common.utils.math.MathUtils;
import com.evalkit.framework.common.utils.random.UuidUtils;
import com.evalkit.framework.eval.node.dataloader.datagen.config.EvalCaseDataGeneratorConfig;
import com.evalkit.framework.eval.node.dataloader.datagen.querygen.QueryGenerator;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 多轮Query数据生成器
 */
public class EvalCaseDataGenerator extends DataGenerator {
    /* 单任务超时时间 */
    protected final static long SINGLE_TASK_TIMEOUT = 60 * 10;
    /* 评测用例生成器配置 */
    protected final EvalCaseDataGeneratorConfig config;

    public EvalCaseDataGenerator(EvalCaseDataGeneratorConfig config) {
        super(config);
        this.config = config;
    }

    /**
     * 并发批量生成评测数据
     *
     * @return 评测数据集
     */
    @Override
    public List<Map<String, Object>> generate() {
        List<String> sessionIs = new ArrayList<>();
        for (int i = 0; i < config.getGenCount(); i++) {
            sessionIs.add(genSessionId());
        }
        // 并发生成单会话的评测数据
        List<List<Map<String, Object>>> singleSessionResultList = BatchRunner.runBatch(sessionIs, this::singleSessionGenerate, PoolName.DATA_GENERATOR, config.getThreadNum(), size -> size * SINGLE_TASK_TIMEOUT);
        if (CollectionUtils.isEmpty(singleSessionResultList)) {
            throw new IllegalArgumentException("Generate eval case data failed");
        }
        return singleSessionResultList.stream().flatMap(List::stream).collect(Collectors.toList());
    }

    /**
     * 生成单个session的评测数据
     *
     * @return 单session评测数据
     */
    protected List<Map<String, Object>> singleSessionGenerate(String sessionId) {
        List<Map<String, Object>> result = new ArrayList<>();
        // 字段key
        String sessionFieldKey = config.getSessionFieldKey();
        String roundFieldKey = config.getRoundFieldKey();
        String queryFieldKey = config.getQueryFieldKey();
        String groundTruthFieldKey = config.getGroundTruthFieldKey();
        String intentFieldKey = config.getIntentFieldKey();
        String contextDependencyFieldKey = config.getContextDependencyFieldKey();
        // 如果开启了随机轮次则从1~round中随机一个轮次
        int roundCount = config.getRoundCount();
        boolean isRandomRound = config.isRandomRound();
        int realRound = isRandomRound ? MathUtils.random(1, roundCount) : roundCount;
        StringBuilder contextDependency = new StringBuilder();
        for (int i = 1; i <= realRound; i++) {
            Map<String, Object> roundData = new HashMap<>();
            // session
            roundData.put(sessionFieldKey, sessionId);
            // round
            roundData.put(roundFieldKey, i);
            // Query
            String query = prepareQuery();
            roundData.put(queryFieldKey, query);
            // 标准答案
            String groundTruth = prepareGroundTruth();
            roundData.put(groundTruthFieldKey, groundTruth);
            // 意图
            String intent = prepareIntent();
            roundData.put(intentFieldKey, intent);
            // 上下文依赖
            roundData.put(contextDependencyFieldKey, contextDependency.toString());
            contextDependency.append(String.format("%s: %s\n", i, query));
            // 额外字段
            Map<String, Object> extra = prepareExtra();
            roundData.putAll(extra);
            // 添加到会话集合
            result.add(roundData);
        }
        return result;
    }

    protected String genSessionId() {
        return UuidUtils.generateUuid();
    }

    protected String prepareQuery() {
        QueryGenerator queryGenerator = config.getQueryGenerator();
        return queryGenerator.generate().stream().findFirst().orElse(null);
    }

    protected String prepareGroundTruth() {
        return "";
    }

    protected String prepareIntent() {
        return "";
    }

    protected Map<String, Object> prepareExtra() {
        return MapUtils.of("extra", "");
    }
}
