package com.evalkit.framework.eval.node.scorer.checker;

import com.evalkit.framework.common.utils.convert.TypeConvertUtils;
import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.common.utils.string.RegexUtils;
import com.evalkit.framework.eval.exception.EvalException;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.node.scorer.checker.config.LLMBasedCheckerConfig;
import com.evalkit.framework.eval.node.scorer.checker.constants.CheckMethod;
import com.evalkit.framework.eval.node.scorer.checker.model.CheckItem;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于LLM的检查器
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public abstract class LLMBasedChecker extends AbstractChecker {
    private LLMBasedCheckerConfig config;

    public LLMBasedChecker(LLMBasedCheckerConfig config) {
        super(config);
        this.config = config;
    }

    @Override
    protected abstract List<CheckItem> prepareCheckItems(DataItem dataItem);

    /**
     * 准备第round轮的输入数据
     */
    protected abstract String prepareUserPrompt(DataItem dataItem, int round);

    /**
     * 第round轮是否执行检查
     */
    protected abstract boolean needCheck(DataItem dataItem, int round);

    @Override
    public abstract boolean support(DataItem dataItem);

    @Override
    protected void check(DataItem dataItem) {
        Map<String, Object> checkMap = buildCheckMap(config.getCheckItems());
        for (int round = config.getBeginRound(); round <= config.getEndRound(); round++) {
            if (needCheck(dataItem, round)) {
                doCheck(dataItem, checkMap, round);
            }
        }
    }

    /**
     * 单轮检查
     */
    protected void doCheck(DataItem dataItem, Map<String, Object> checkMap, int round) {
        String userPrompt = prepareUserPrompt(dataItem, round);
        String prompt = generateLLMCheckPrompt(checkMap, userPrompt);
        Map<String, Map<String, Object>> checkResultMap = llmCheck(prompt);
        updateCheckItems(config.getCheckItems(), checkResultMap);
    }

    /**
     * 构建检查表,检查项列表转成Map,检查项的名称作为key
     * <p>
     * {
     * "检查项名称":{
     * "score":"检查描述",
     * "reason":"推理思考过程以及最终得分解释"
     * }
     * }
     */
    protected Map<String, Object> buildCheckMap(List<CheckItem> checkItems) {
        Map<String, Object> checkMap = new HashMap<>();
        for (CheckItem checkItem : checkItems) {
            Map<String, Object> singleCheckMap = convertToLLMCheckMap(checkItem);
            checkMap.putAll(singleCheckMap);
        }
        return checkMap;
    }

    /**
     * 检查项转Map
     */
    protected Map<String, Object> convertToLLMCheckMap(CheckItem checkItem) {
        Map<String, Object> map = new LinkedHashMap<>();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("score", checkItem.getCheckDescription());
        r.put("reason", "推理思考过程以及最终得分解释");
        map.put(checkItem.getName(), r);
        return map;
    }

    /**
     * 构建检查prompt
     */
    protected String generateLLMCheckPrompt(Map<String, Object> checkMap, String userPrompt) {
        if (StringUtils.isEmpty(userPrompt)) {
            throw new EvalException("User prompt is empty");
        }
        String checkMapJson = JsonUtils.toJson(checkMap);
        return String.format("%s\n检查项要求如下:\n%s\n以下是用户输入数据:\n%s", config.getSysPrompt(), checkMapJson, userPrompt);
    }

    /**
     * 大模型检查
     */
    public Map<String, Map<String, Object>> llmCheck(String prompt) {
        // LLM对话&解析结果
        String chatResultJson;
        String chatResult = config.getLlmService().chat(prompt);
        log.info("prompt:{}\nchat result:{}", prompt, chatResult);
        chatResultJson = RegexUtils.extractMarkdownJsonBlock(chatResult);
        if (StringUtils.isEmpty(chatResultJson)) {
            chatResultJson = chatResult;
        }
        if (StringUtils.isEmpty(chatResultJson)) {
            throw new EvalException("Can not parse LLM chat result: " + chatResult);
        }
        return JsonUtils.fromJson(chatResultJson, new TypeReference<Map<String, Map<String, Object>>>() {
        });
    }

    /**
     * 更新checkItem
     */
    public void updateCheckItems(List<CheckItem> checkItems, Map<String, Map<String, Object>> checkResultMap) {
        for (CheckItem checkItem : checkItems) {
            // 如果检查项不支持检查则跳过
            if (!checkItem.isSupport()) continue;
            String checkItemName = checkItem.getName();
            Map<String, Object> checkItemResult = checkResultMap.getOrDefault(checkItemName, null);
            if (checkItemResult != null) {
                double score = TypeConvertUtils.toDouble(checkItemResult.getOrDefault("score", 0.0));
                String reason = TypeConvertUtils.toString(checkItemResult.getOrDefault("reason", ""));
                if (!checkItem.isExecuted()) {
                    checkItem.setScore(score);
                    checkItem.setReason(reason);
                    checkItem.setCheckMethod(CheckMethod.LLM);
                    checkItem.setExecuted(true);
                } else {
                    checkItem.setScore((checkItem.getScore() + score) / 2);
                    checkItem.setReason(checkItem.getReason() + " | " + reason);
                }
            }
        }
    }
}
