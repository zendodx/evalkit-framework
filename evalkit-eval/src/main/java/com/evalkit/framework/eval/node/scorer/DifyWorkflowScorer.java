package com.evalkit.framework.eval.node.scorer;

import com.evalkit.framework.eval.model.ApiCompletionResult;
import com.evalkit.framework.eval.model.DataItem;
import com.evalkit.framework.eval.model.InputData;
import com.evalkit.framework.eval.model.ScorerResult;
import com.evalkit.framework.eval.node.scorer.config.DifyWorkflowScorerConfig;
import io.github.imfangs.dify.client.DifyClientFactory;
import io.github.imfangs.dify.client.DifyWorkflowClient;
import io.github.imfangs.dify.client.enums.ResponseMode;
import io.github.imfangs.dify.client.exception.DifyApiException;
import io.github.imfangs.dify.client.model.workflow.WorkflowRunRequest;
import io.github.imfangs.dify.client.model.workflow.WorkflowRunResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Map;

/**
 * 基于Dify工作流的评估器
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public abstract class DifyWorkflowScorer extends Scorer {
    protected final DifyWorkflowClient client;
    protected final DifyWorkflowScorerConfig config;

    public DifyWorkflowScorer(DifyWorkflowScorerConfig config) {
        super(config);
        validConfig(config);
        this.config = config;
        this.client = DifyClientFactory.createWorkflowClient(config.getBaseUrl(), config.getApiKey());
    }

    protected void validConfig(DifyWorkflowScorerConfig config) {
        super.validConfig(config);
        if (StringUtils.isEmpty(config.getApiKey())) {
            throw new IllegalArgumentException("apiKey must not be empty");
        }
        if (StringUtils.isEmpty(config.getBaseUrl())) {
            throw new IllegalArgumentException("baseUrl must not be empty");
        }
        if (StringUtils.isEmpty(config.getUserName())) {
            throw new IllegalArgumentException("userName must not be empty");
        }
    }

    /**
     * 准备工作流输入参数
     */
    public abstract Map<String, Object> prepareInputParams(InputData inputData, ApiCompletionResult apiCompletionResult);

    /**
     * 解析工作流结果构造评测结果
     */
    public abstract ScorerResult prepareScorerResult(InputData inputData, ApiCompletionResult apiCompletionResult, Map<String, Object> outputs);

    @Override
    public ScorerResult eval(DataItem dataItem) throws DifyApiException, IOException {
        WorkflowRunRequest request = null;
        InputData inputData = dataItem.getInputData();
        ApiCompletionResult apiCompletionResult = dataItem.getApiCompletionResult();
        try {
            request = WorkflowRunRequest.builder()
                    .inputs(prepareInputParams(inputData, apiCompletionResult))
                    .responseMode(ResponseMode.BLOCKING)
                    .user(config.getUserName())
                    .build();
            WorkflowRunResponse response = client.runWorkflow(request);
            WorkflowRunResponse.WorkflowRunData data = response.getData();
            Map<String, Object> outputs = data.getOutputs();
            log.info("Dify workflow execute success, request:{}, output: {}", request, outputs);
            return prepareScorerResult(inputData, apiCompletionResult, outputs);
        } catch (Exception e) {
            log.error("Dify workflow execute failed, request: {}", request, e);
            throw e;
        }
    }
}