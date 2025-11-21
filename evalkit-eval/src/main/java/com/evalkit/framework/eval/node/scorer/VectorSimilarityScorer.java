package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.common.utils.nlp.NLPUtils;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.scorer.config.VectorSimilarityScorerConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;

/**
 * 向量相似度评估器,使用 TF-IDF + 余弦相似度（适合传统 NLP）
 */
@EqualsAndHashCode(callSuper = true)
@Data
public abstract class VectorSimilarityScorer extends Scorer {
    protected VectorSimilarityScorerConfig config;

    public VectorSimilarityScorer() {
        this(VectorSimilarityScorerConfig.builder().build());
    }

    public VectorSimilarityScorer(VectorSimilarityScorerConfig config) {
        super(config);
        this.config = config;
    }

    protected void validConfig(VectorSimilarityScorerConfig config) {
        super.validConfig(config);
        if (config.getSimilarityThreshold() < 0 || config.getSimilarityThreshold() > 1) {
            throw new IllegalArgumentException("similarityThreshold must be in [0, 1]");
        }
    }

    /**
     * 选择要计算相似度的两个字段
     */
    public abstract Pair<String, String> prepareFieldPair(DataItem dataItem);

    @Override
    public ScorerResult eval(DataItem dataItem) throws Exception {
        Pair<String, String> pair = prepareFieldPair(dataItem);
        String left = pair.getLeft();
        String right = pair.getRight();
        double similarity = NLPUtils.cosineSimilarity(left, right);
        double score;
        String reason;
        double similarityThreshold = config.getSimilarityThreshold();
        if (similarity > similarityThreshold) {
            score = 1;
            reason = String.format("相似度为%.4f，大于阈值%.4f", similarity, similarityThreshold);
        } else {
            score = 0;
            reason = String.format("相似度为%.4f，小于阈值%.4f", similarity, similarityThreshold);
        }
        Map<String, Object> extra = new HashMap<>();
        extra.put("similarity", similarity);
        extra.put("similarityThreshold", similarityThreshold);
        ScorerResult scorerResult = new ScorerResult();
        scorerResult.setMetric(config.getMetricName());
        scorerResult.setScore(score);
        scorerResult.setReason(reason);
        scorerResult.setExtra(extra);
        return scorerResult;
    }
}
