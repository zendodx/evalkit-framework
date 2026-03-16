package com.evalkit.framework.eval.node.data_generator.filter;

import com.evalkit.framework.eval.node.data_generator.model.Turn;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 相似性过滤
 */
public class SimilarityFilter {
    // 计算两个向量的余弦相似度
    public static double cosineSimilarity(List<Double> v1, List<Double> v2) {
        double dotProduct = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < v1.size(); i++) {
            dotProduct += v1.get(i) * v2.get(i);
            normA += Math.pow(v1.get(i), 2);
            normB += Math.pow(v2.get(i), 2);
        }
        return (normA == 0.0 || normB == 0.0) ? 0.0 : dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // 将多轮对话拼接成一个长文本用于计算向量
    public static String combineDialogue(List<Turn> dialogue) {
        return dialogue.stream().map(t -> t.query).collect(Collectors.joining(" "));
    }
}
