package com.evalkit.framework.infra.service.llm;

import com.evalkit.framework.common.utils.json.JsonUtils;
import com.evalkit.framework.common.utils.llm.LLMTokenUtils;
import com.evalkit.framework.infra.service.llm.config.LLMServiceConfig;
import com.evalkit.framework.infra.service.llm.constants.LLMResponseType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 大模型服务抽象类
 */
@Slf4j
public abstract class AbstractLLMService implements LLMService {
    protected LLMServiceConfig config;

    public AbstractLLMService(LLMServiceConfig config) {
        validConfig(config);
        this.config = config;
    }

    public LLMServiceConfig getConfig() {
        return config;
    }

    public void setConfig(LLMServiceConfig config) {
        this.config = config;
    }

    /**
     * 校验配置
     *
     * @param config 配置信息
     */
    protected void validConfig(LLMServiceConfig config) {
        // model不能为空
        if (StringUtils.isEmpty(config.getModel())) {
            throw new IllegalArgumentException("LLM service model cannot be empty");
        }
    }

    /**
     * 实际执行大模型对话
     *
     * @param prompt 提示词
     * @return 大模型回复
     */
    public abstract String doChat(String prompt);

    @Override
    public String chat(String prompt) {
        boolean openRetry = config.isOpenRetry();
        int retryTimes = config.getRetryTimes();
        long retryInterval = config.getRetryInterval();
        TimeUnit retryTimeUnit = config.getRetryTimeUnit();
        String result = null;
        for (int i = 0; i < retryTimes; i++) {
            try {
                result = doChat(prompt);
                validResponse(result);
                // 计算耗费
                long allToken = 0;
                long inToken = 0;
                long outToken = 0;
                double inPrice = 0.0;
                double outPrice = 0.0;
                double totalPrice = 0.0;
                String model = config.getModel();
                try {
                    inToken = LLMTokenUtils.tokenCount(prompt);
                    outToken = LLMTokenUtils.tokenCount(result);
                    allToken = inToken + outToken;
                    inPrice = LLMTokenUtils.calTokenPrice(prompt, config.getInPrice());
                    outPrice = LLMTokenUtils.calTokenPrice(prompt, config.getOutPrice());
                    totalPrice = inPrice + outPrice;
                    // 全局打点上报
                    LLMTokenMetrics.record(model, inToken, outToken, inPrice, outPrice);
                } catch (Exception ignored) {
                    log.error("LLM service count token and price failed, error: {}", ignored.getMessage(), ignored);
                }
                log.info("LLM service chat success, prompt:{}, response:{}, model:{}, in token: {}, out token:{}, all token: {}, in price:{}, out price: {}, totalPrice: {}",
                        prompt, result, model, inToken, outToken, allToken, inPrice, outPrice, totalPrice);
                break;
            } catch (Throwable e) {
                // 没有开启重试,直接抛异常结束
                if (!openRetry) {
                    log.error("LLM service chat failed without retry, error: {}", e.getMessage(), e);
                    throw e;
                }
                // 开启重试,但达到最大重试次数,抛异常结束
                if (i == retryTimes - 1) {
                    log.error("LLM service chat failed after retry {} times, error: {}", retryTimes, e.getMessage(), e);
                    throw e;
                }
                log.error("LLM service chat failed, retry {} times, error: {}", i + 1, e.getMessage(), e);
                // 等待一段时间后重试
                try {
                    long retryIntervalMillis = retryTimeUnit.toMillis(retryInterval);
                    Thread.sleep(retryIntervalMillis);
                } catch (InterruptedException e2) {
                    log.error("LLM service chat retry thread sleep failed, error: {}", e2.getMessage(), e2);
                }
            }
        }
        return result;
    }

    /**
     * 校验大模型回复
     *
     * @param response 大模型回复
     */
    protected void validResponse(String response) {
        LLMResponseType responseType = config.getResponseType();
        // 如果是大模型回复是JSON类型要检查是否正确
        if (responseType == LLMResponseType.JSON) {
            // 替换掉可能存在的json标识符
            String replace = StringUtils.replaceChars(response, "```json", "");
            replace = StringUtils.replaceChars(replace, "```", "");
            try {
                JsonUtils.fromJson(replace, Object.class);
            } catch (Exception e) {
                log.error("LLM service response is not json, response: {}", response, e);
                throw e;
            }
        }
    }

    @Override
    public String getModel() {
        return config.getModel();
    }
}
