package com.evalkit.framework.common.utils.list;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ListUtils {
    private ListUtils() {
    }

    /**
     * 快速创建列表
     */
    public static <T> List<T> of(T... elements) {
        return new ArrayList<>(Arrays.asList(elements));
    }
}
