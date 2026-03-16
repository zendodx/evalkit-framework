package com.evalkit.framework.eval.node.data_generator.prompt;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.eval.node.data_generator.model.GoldenCase;
import freemarker.template.Configuration;
import freemarker.template.Template;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * 提示词引擎
 */
public class PromptEngine {
    private final Configuration cfg;

    public PromptEngine() {
        cfg = new Configuration(Configuration.VERSION_2_3_32);

        freemarker.template.DefaultObjectWrapperBuilder owb =
                new freemarker.template.DefaultObjectWrapperBuilder(Configuration.VERSION_2_3_32);
        owb.setExposeFields(true);
        cfg.setObjectWrapper(owb.build());

        freemarker.cache.StringTemplateLoader stringLoader = new freemarker.cache.StringTemplateLoader();
        String templateContent =
                "你是一个高级AI对话评测数据生成专家。你的任务是基于【参考示例】的对话逻辑，使用【新输入数据】生成一组全新的多轮对话。\n\n" +
                        "【核心生成约束（非常重要）】\n" +
                        "1. 逻辑一致：必须严格保持与示例相同的轮次数，且每一轮的【对话意图】和【上下文跳转逻辑】必须与示例完全对应。\n" +
                        "2. 实体替换：必须将对话中的核心实体准确替换为【新输入数据】中提供的内容。\n" +
                        "3. 语言泛化（拒绝抄袭）：绝对不能原封不动地照抄示例的句式！你必须像一个真实的、语气自然的用户一样，使用不同的词汇、同义句型或口语化表达来【改写】每一句话。可以适当增加自然的寒暄或语气词。\n\n" +
                        "4. 占位符保护：如果参考示例中包含类似 {{...}} 的占位符（如 {{DATE_TOMORROW}}），你在生成新的 Query 时，【必须原封不动地保留该占位符】，绝对不能修改或删除它！\n" +
                        "5. 代词与省略：在多轮对话的后续轮次（如确认预订时），请尽量模仿真实用户使用代词（如“那就订刚才那个航班吧”、“把这两样一起下单”），【绝对不要】在最后一轮重复说出具体的航班号或房间名！\n" +
                        "【参考示例】\n" +
                        "输入数据：${goldenCaseKgDataJson}\n" +
                        "输出对话：\n" +
                        "<#list goldenCase.dialogue as turn>\n" +
                        "第${turn.turn}轮: ${turn.query}\n" +
                        "</#list>\n\n" +
                        "【本次任务】\n" +
                        "新输入数据：${newKgDataJson}\n\n" +
                        "【输出格式要求】\n" +
                        "必须输出合法的 JSON 数组格式。数组中的每个对象必须且只能包含 \"turn\" (整数型) 和 \"query\" (字符串型) 两个字段。\n" +
                        "请严格按照以下骨架输出，不要包含任何其他解释性文字：\n" +
                        "[\n" +
                        "  {\n" +
                        "    \"turn\": 1,\n" +
                        "    \"query\": \"(用全新的自然语言表达第一轮意图)\"\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"turn\": 2,\n" +
                        "    \"query\": \"(用全新的自然语言表达第二轮意图)\"\n" +
                        "  }\n" +
                        "]\n";

        stringLoader.putTemplate("promptTemplate", templateContent);
        cfg.setTemplateLoader(stringLoader);
    }

    public String generatePrompt(GoldenCase goldenCase, Map<String, String> newKgData) throws Exception {
        Template template = cfg.getTemplate("promptTemplate");
        Map<String, Object> dataModel = new HashMap<>();

        String goldenCaseKgDataJson = JsonUtils.toJson(goldenCase.kgDataUsed);
        String newKgDataJson = JsonUtils.toJson(newKgData);

        dataModel.put("goldenCase", goldenCase);
        dataModel.put("goldenCaseKgDataJson", goldenCaseKgDataJson);
        dataModel.put("newKgDataJson", newKgDataJson);

        StringWriter out = new StringWriter();
        template.process(dataModel, out);
        return out.toString();
    }
}
