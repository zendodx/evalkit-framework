package com.evalkit.framework.common.client.deepseek;

import com.evalkit.framework.common.client.deepseek.request.chat.ChatCompletionNamedToolChoice;
import com.evalkit.framework.common.client.deepseek.request.chat.DeepseekChatCompletionsRequest;
import com.evalkit.framework.common.client.deepseek.request.chat.Tool;
import com.evalkit.framework.common.client.deepseek.response.ListModelResponse;
import com.evalkit.framework.common.client.deepseek.response.UserBalanceResponse;
import com.evalkit.framework.common.client.deepseek.response.chat.DeepseekChatCompletionsResponse;
import com.evalkit.framework.common.client.deepseek.response.chat.DeepseekDeepseekChatCompletionsNoStreamResponse;
import com.evalkit.framework.common.client.deepseek.response.chat.DeepseekDeepseekChatCompletionsStreamResponse;
import com.evalkit.framework.common.client.deepseek.response.chat.SSEData;
import com.evalkit.framework.common.utils.json.JsonUtils;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Streaming;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * deepseekLLM接入文档: https://api-docs.deepseek.com/zh-cn/
 */
public enum DeepseekClient {
    INSTANCE;

    private interface ApiService {
        @GET("models")
        Call<ListModelResponse> listModel();

        @GET("user/balance")
        Call<UserBalanceResponse> getUserBalance();

        @Streaming
        @POST("chat/completions")
        Call<ResponseBody> chatCompletionsStream(@Body DeepseekChatCompletionsRequest deepseekChatCompletionsRequest);

        @POST("chat/completions")
        Call<ResponseBody> chatCompletionsNoStream(@Body DeepseekChatCompletionsRequest deepseekChatCompletionsRequest);
    }

    private final static String baseUrl = "https://api.deepseek.com/";
    private static ApiService apiService;

    public void initClient(String accessToken) {
        initClient(accessToken, 60, TimeUnit.SECONDS);
    }

    public void initClient(String accessToken, long timeout, TimeUnit unit) {
        initClient(accessToken, timeout, timeout, timeout, unit);
    }

    public void initClient(String accessToken, long connectTimeout, long readTimeout, long writeTimeout, TimeUnit timeUnit) {
        assert StringUtils.isNotEmpty(accessToken);

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeout, timeUnit)
                .readTimeout(readTimeout, timeUnit)
                .writeTimeout(writeTimeout, timeUnit)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request.Builder requestBuilder = original.newBuilder()
                            .addHeader("Authorization", "Bearer " + accessToken)
                            .addHeader("Content-Type", "application/json");
                    Request request = requestBuilder.build();
                    return chain.proceed(request);
                }).build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(JacksonConverterFactory.create())
                .build();
        apiService = retrofit.create(ApiService.class);
    }


    public ListModelResponse listModel() throws IOException {
        Response<ListModelResponse> execute = apiService.listModel().execute();
        if (execute.isSuccessful()) {
            return execute.body();
        } else {
            throw new RuntimeException();
        }
    }

    public UserBalanceResponse getUserBalance() throws IOException {
        Response<UserBalanceResponse> execute = apiService.getUserBalance().execute();
        if (execute.isSuccessful()) {
            return execute.body();
        } else {
            throw new RuntimeException();
        }
    }

    public DeepseekChatCompletionsResponse chatCompletions(DeepseekChatCompletionsRequest deepseekChatCompletionsRequest) throws IOException {
        if (!validChatCompletionsRequestParam(deepseekChatCompletionsRequest)) {
            throw new RuntimeException("Invalid chatCompletionsRequest");
        }
        boolean isStream = deepseekChatCompletionsRequest.getStream();
        if (isStream) {
            Response<ResponseBody> execute = apiService.chatCompletionsStream(deepseekChatCompletionsRequest).execute();
            return convertStreamResponse(execute);
        } else {
            Response<ResponseBody> execute = apiService.chatCompletionsNoStream(deepseekChatCompletionsRequest).execute();
            return convertNoStreamResponse(execute);
        }
    }

    private DeepseekDeepseekChatCompletionsStreamResponse convertStreamResponse(Response<ResponseBody> execute) throws IOException {
        if (execute.isSuccessful() && execute.body() != null) {
            List<SSEData> sseDatas = new ArrayList<>();
            try (InputStream inputStream = execute.body().byteStream()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                StringBuilder rawData = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    rawData.append(line).append("\r\n");
                    if (line.startsWith("data: ")) {
                        line = line.substring(6);
                        if (line.equals("[DONE]")) {
                            break;
                        }
                        SSEData sseData = JsonUtils.fromJson(line, SSEData.class);
                        sseDatas.add(sseData);
                    }
                }
                inputStream.close();
                return new DeepseekDeepseekChatCompletionsStreamResponse(sseDatas, rawData.toString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return null;
    }

    private DeepseekDeepseekChatCompletionsNoStreamResponse convertNoStreamResponse(Response<ResponseBody> execute) throws IOException {
        if (execute.isSuccessful() && execute.body() != null) {
            String bodyStr = execute.body().string();
            return JsonUtils.fromJson(bodyStr, DeepseekDeepseekChatCompletionsNoStreamResponse.class);
        }
        return null;
    }

    private boolean validChatCompletionsRequestParam(DeepseekChatCompletionsRequest request) {
        if (request == null) {
            return false;
        }
        boolean valid = true;
        // messages参数检查
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            valid = false;
        }
        // model参数检查
        if (StringUtils.isEmpty(request.getModel())) {
            valid = false;
        }
        // tools参数检查
        List<Tool> tools = request.getTools();
        if (tools != null && !tools.isEmpty()) {
            boolean isToolsParamError = tools.stream().anyMatch(tool -> {
                if (StringUtils.isEmpty(tool.getType())) {
                    return true;
                }
                if (tool.getFunction() == null) {
                    return true;
                } else {
                    return StringUtils.isEmpty(tool.getFunction().getName());
                }
            });
            valid = !isToolsParamError;
        }
        // tool_choice参数检查
        Object toolChoice = request.getToolChoice();
        if (toolChoice != null) {
            if (toolChoice instanceof String) {
                valid = StringUtils.isNotEmpty((CharSequence) toolChoice);
            } else if (toolChoice instanceof ChatCompletionNamedToolChoice) {
                String type = ((ChatCompletionNamedToolChoice) toolChoice).getType();
                valid = StringUtils.isNotEmpty(type);
                ChatCompletionNamedToolChoice.Function function = ((ChatCompletionNamedToolChoice) toolChoice).getFunction();
                if (function == null) {
                    valid = false;
                } else {
                    valid = StringUtils.isNotEmpty(function.getName());
                }
            }
        }
        return valid;
    }
}
