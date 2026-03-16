package com.evalkit.framework.eval.node.data_generator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
public class Turn {
    public int turn;
    public String query;
    @JsonIgnoreProperties
    public String assertType;
    // 用于存放最终运行时注入的真实图谱数据 (如 ["熊猫主题客栈"])
    @JsonIgnoreProperties
    public List<String> expectedKeywords;
    // 用于接收配置中的变量名映射 (如 ["hotelName"])
    public List<String> expectedVars;
}
