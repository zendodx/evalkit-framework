package com.evalkit.framework.infra.service.llm;

import com.evalkit.framework.infra.service.llm.config.LLMServiceConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 大模型服务抽象类
 */
@Slf4j
public abstract class AbstractLLMService implements LLMService {
    protected final LLMServiceConfig config;

    public AbstractLLMService(LLMServiceConfig config) {
        this.config = config;
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
                break;
            } catch (Exception e) {
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

    @Override
    public String getModel() {
        return config.getModel();
    }
}
