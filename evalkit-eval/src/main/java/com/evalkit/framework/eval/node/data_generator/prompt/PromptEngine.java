package com.evalkit.framework.eval.node.data_generator.prompt;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.eval.node.data_generator.model.GoldenCase;
import com.evalkit.framework.eval.node.data_generator.model.Turn;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Scanner;

/**
 * 提示词引擎：从 classpath 下的 Markdown 文件加载模板，替换占位符后生成最终 Prompt。
 * 模板文件路径：prompts/kg_dialogue_generator.md
 */
public class PromptEngine {

    private static final String TEMPLATE_PATH = "prompts/kg_dialogue_generator.md";

    private static final String PLACEHOLDER_GOLDEN_KG_JSON = "${GOLDEN_CASE_KG_DATA_JSON}";
    private static final String PLACEHOLDER_GOLDEN_DIALOGUE = "${GOLDEN_CASE_DIALOGUE}";
    private static final String PLACEHOLDER_NEW_KG_JSON = "${NEW_KG_DATA_JSON}";

    /** 缓存模板内容，避免每次调用都重复读取文件 */
    private static volatile String templateCache;

    public String generatePrompt(GoldenCase goldenCase, Map<String, String> newKgData) {
        String template = loadTemplate();

        String goldenCaseKgDataJson = JsonUtils.toJson(goldenCase.kgDataUsed);
        String newKgDataJson = JsonUtils.toJson(newKgData);

        // 构建参考示例的对话轮次文本
        StringBuilder dialogueBuilder = new StringBuilder();
        for (Turn turn : goldenCase.dialogue) {
            dialogueBuilder.append("第").append(turn.turn).append("轮: ").append(turn.query).append("\n");
        }

        return template
                .replace(PLACEHOLDER_GOLDEN_KG_JSON, goldenCaseKgDataJson)
                .replace(PLACEHOLDER_GOLDEN_DIALOGUE, dialogueBuilder.toString())
                .replace(PLACEHOLDER_NEW_KG_JSON, newKgDataJson);
    }

    private static String loadTemplate() {
        if (templateCache != null) {
            return templateCache;
        }
        synchronized (PromptEngine.class) {
            if (templateCache != null) {
                return templateCache;
            }
            try (InputStream is = PromptEngine.class.getClassLoader().getResourceAsStream(TEMPLATE_PATH)) {
                if (is == null) {
                    throw new IllegalStateException("Prompt template not found on classpath: " + TEMPLATE_PATH);
                }
                try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name())) {
                    scanner.useDelimiter("\\A");
                    templateCache = scanner.hasNext() ? scanner.next() : "";
                }
            } catch (Exception e) {
                throw new IllegalStateException("Failed to load prompt template: " + TEMPLATE_PATH, e);
            }
            return templateCache;
        }
    }
}
