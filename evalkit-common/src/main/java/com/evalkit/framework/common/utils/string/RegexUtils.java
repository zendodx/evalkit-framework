package com.evalkit.framework.common.utils.string;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正则表达式工具类
 */
public class RegexUtils {

    private RegexUtils() {
    }

    public static String extractMarkdownJsonBlock(String text) {
        if (StringUtils.isEmpty(text)) {
            return text;
        }
        String regex = "```json\\s*([\\s\\S]*?)\\s*```";
        String res = RegexUtils.extractFirst(text, regex, 1, true);
        if (StringUtils.isNotEmpty(res)) {
            return res;
        }
        return text;
    }

    public static String extractFirst(String text, String regex, int groupIndex, boolean dotAll) {
        if (text == null || regex == null) return null;
        int flags = dotAll ? Pattern.DOTALL : 0;
        Matcher m = Pattern.compile(regex, flags).matcher(text);
        return m.find() ? m.group(groupIndex) : null;
    }

    public static List<String> extractAll(String text, String regex, int groupIndex, boolean dotAll) {
        List<String> list = new ArrayList<>();
        if (text == null || regex == null) return list;
        int flags = dotAll ? Pattern.DOTALL : 0;
        Matcher m = Pattern.compile(regex, flags).matcher(text);
        while (m.find()) {
            list.add(m.group(groupIndex));
        }
        return list;
    }
}
