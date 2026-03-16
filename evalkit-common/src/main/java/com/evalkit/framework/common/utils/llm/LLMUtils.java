package com.evalkit.framework.common.utils.llm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM工具类
 */
public class LLMUtils {
    private LLMUtils() {
    }

    /**
     * 提取 LLM 返回文本中 ```json 和 ``` 之间的纯 JSON 内容。
     * 具备极强的容错性，能处理带有废话、缺少 json 标识、甚至缺少代码块的情况。
     */
    public static String extractLLMJsonResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "";
        }

        String text = response.trim();

        // 策略 1：使用正则表达式提取 Markdown 代码块中的内容
        // (?is) 开启忽略大小写 (i) 和单行模式 (s，使 . 能匹配换行符)
        // ```(?:json)? 匹配 ``` 加上可选的 json (不区分大小写)
        // \\s*(.*?)\\s*``` 懒惰匹配中间的内容，直到遇到闭合的 ```
        Pattern pattern = Pattern.compile("(?is)```(?:json)?\\s*(.*?)\\s*```");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            // Group 1 就是括号 (.*?) 捕获到的纯 JSON 内容
            return matcher.group(1).trim();
        }

        // 策略 2：降级容错处理 (Fallback)
        // 如果 LLM 根本没有输出 ``` 代码块，但输出了废话 + JSON + 废话
        // 我们尝试直接寻找 JSON 的边界符号：第一个 '[' 或 '{'，以及最后一个 ']' 或 '}'
        int firstArrayIdx = text.indexOf('[');
        int firstObjectIdx = text.indexOf('{');

        // 找到最先出现的 JSON 起始符
        int startIndex = -1;
        if (firstArrayIdx != -1 && firstObjectIdx != -1) {
            startIndex = Math.min(firstArrayIdx, firstObjectIdx);
        } else {
            startIndex = Math.max(firstArrayIdx, firstObjectIdx);
        }

        if (startIndex != -1) {
            // 找到最后出现的 JSON 结束符
            int lastArrayIdx = text.lastIndexOf(']');
            int lastObjectIdx = text.lastIndexOf('}');
            int endIndex = Math.max(lastArrayIdx, lastObjectIdx);

            if (endIndex > startIndex) {
                // 截取起始符到结束符之间的内容
                return text.substring(startIndex, endIndex + 1).trim();
            }
        }

        // 策略 3：如果连括号都找不到，只能死马当活马医，直接返回原文本
        return text;
    }
}
