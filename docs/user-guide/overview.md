# 概述与快速开始

## 框架简介

EvalKit Framework 是一个基于 Java 的 **自动化 AI 评测框架**，以 DAG（有向无环图）工作流为核心，让你可以像搭积木一样把"加载数据 → 调用接口 → 评分 → 统计 → 上报"几个环节串联起来，快速完成对大模型或 AI 业务接口的自动化评测。

**核心特性**

| 特性 | 说明 |
|------|------|
| **DAG 工作流** | 基于有向无环图并行执行，多个评估器可以同时跑 |
| **三种评测模式** | DAG 直接评测 / 全量评测 / 增量断点续评 |
| **高扩展性** | 所有节点均可继承重写，不依赖 Spring |
| **内置 LLM 集成** | 一行代码接入 OpenAI、DeepSeek 等大模型 |
| **轻量依赖** | 不引入 Spring，可嵌入任意 Java 项目 |

## 环境要求

- JDK 1.8+
- Maven 3.6+

## Maven 引入

```xml
<dependency>
    <groupId>io.github.zendodx</groupId>
    <artifactId>evalkit-eval</artifactId>
    <version>1.2.2</version>
</dependency>
```

> 最新版本见 [发版历史](../changelog.md)

---

## 整体架构

一次评测任务由以下节点组成，节点之间通过 `WorkflowBuilder.link()` 连接：

```
Begin（全局配置）
  └─ DataLoader（加载评测数据）
       └─ [DataLoaderWrapper]（可选：Mock/润色数据）
            └─ ApiCompletion（调用被测接口）
                 └─ Scorer × N（并行打分）
                      └─ Counter（统计汇总）
                           └─ Reporter × N（输出结果）
                                └─ End（收尾操作）
```

---

## 快速开始

下面用一个完整的最小示例说明如何用 EvalKit 评测一个"旅游攻略"对话接口。

### 场景说明

- 评测数据：一条包含 `query`（问题）和 `groundTruth`（标准答案）的测试用例
- 被测接口：调用某个 AI 接口返回 `response`
- 评估维度：① 语义相似度（与标准答案对比）② 回复长度是否合格

### 完整代码

```java
import com.evalkit.framework.common.utils.list.ListUtils;
import com.evalkit.framework.common.utils.map.MapUtils;
import com.evalkit.framework.eval.model.*;
import com.evalkit.framework.eval.node.api.ApiCompletion;
import com.evalkit.framework.eval.node.begin.Begin;
import com.evalkit.framework.eval.node.counter.BasicCounter;
import com.evalkit.framework.eval.node.dataloader.DataLoader;
import com.evalkit.framework.eval.node.end.End;
import com.evalkit.framework.eval.node.reporter.StdReporter;
import com.evalkit.framework.eval.node.scorer.Scorer;
import com.evalkit.framework.eval.node.scorer.VectorSimilarityScorer;
import com.evalkit.framework.eval.node.scorer.config.ScorerConfig;
import com.evalkit.framework.eval.node.scorer.config.VectorSimilarityScorerConfig;
import com.evalkit.framework.workflow.WorkflowBuilder;
import com.evalkit.framework.workflow.model.WorkflowContext;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.List;

public class QuickStartDemo {

    public static void main(String[] args) {

        // ① Begin：全局配置，此处使用默认打分策略
        Begin begin = new Begin();

        // ② DataLoader：内联提供测试数据
        DataLoader dataLoader = new DataLoader() {
            @Override
            public List<InputData> prepareDataList() {
                return ListUtils.of(
                    new InputData(MapUtils.of(
                        "query",       "帮我制定一份成都3日游攻略",
                        "groundTruth", "成都3日游推荐：第一天宽窄巷子+锦里；第二天大熊猫基地；第三天都江堰"
                    ))
                );
            }
        };

        // ③ ApiCompletion：调用被测的 AI 接口
        ApiCompletion apiCompletion = new ApiCompletion() {
            @Override
            protected ApiCompletionResult invoke(DataItem dataItem) {
                // 这里替换成真实的 AI 接口调用
                String mockResponse = "成都旅游推荐：第一天游览宽窄巷子和锦里，感受老成都文化；" +
                        "第二天前往大熊猫繁育基地；第三天可去都江堰";
                ApiCompletionResult result = new ApiCompletionResult();
                result.setResultItem(MapUtils.of("response", mockResponse));
                return result;
            }
        };

        // ④ 评估器1：语义相似度（TF-IDF 余弦相似度）
        VectorSimilarityScorer similarityScorer = new VectorSimilarityScorer(
                VectorSimilarityScorerConfig.builder()
                        .metricName("语义相似度")
                        .similarityThreshold(0.5)   // 相似度 > 0.5 得 1 分，否则 0 分
                        .build()
        ) {
            @Override
            public org.apache.commons.lang3.tuple.Pair<String, String> prepareFieldPair(DataItem dataItem) {
                // 指定要比较的两个字段
                String groundTruth = dataItem.getInputData().get("groundTruth");
                String response    = dataItem.getApiCompletionResult().get("response");
                return new ImmutablePair<>(groundTruth, response);
            }
        };

        // ④ 评估器2：自定义规则——回复长度是否 >= 20 字
        Scorer lengthScorer = new Scorer(
                ScorerConfig.builder()
                        .metricName("回复长度")
                        .threshold(1.0)             // 必须得 1 分才算通过
                        .build()
        ) {
            @Override
            public ScorerResult eval(DataItem dataItem) {
                String response = dataItem.getApiCompletionResult().get("response");
                ScorerResult result = new ScorerResult();
                result.setMetric("回复长度");
                if (response != null && response.length() >= 20) {
                    result.setScore(1.0);
                    result.setReason("回复长度 " + response.length() + " 字，符合要求");
                } else {
                    result.setScore(0.0);
                    result.setReason("回复过短");
                }
                return result;
            }
        };

        // ⑤ Counter：统计通过率、平均分等汇总指标
        BasicCounter counter = new BasicCounter();

        // ⑥ Reporter：打印到控制台
        StdReporter stdReporter = new StdReporter();

        // ⑦ End：收尾（可做上传附件、发通知等操作）
        End end = new End() {
            @Override
            public void process(WorkflowContext ctx) {
                System.out.println("评测完成！");
            }
        };

        // 组装工作流并执行
        List<Scorer> scorers = ListUtils.of(similarityScorer, lengthScorer);
        new WorkflowBuilder()
                .link(begin, dataLoader, apiCompletion)
                .link(apiCompletion, scorers)       // 两个评估器并行
                .link(scorers, counter)
                .link(counter, stdReporter)
                .link(stdReporter, end)
                .build()
                .execute();
    }
}
```

### 执行结果示例

控制台会打印每条数据的详细评测结果和汇总统计，例如：

```
------------评测Case------------

{"dataIndex":0,"inputData":{"query":"帮我制定一份成都3日游攻略",...},
 "evalResult":{"pass":true,"score":2.0,...}}

------------评测统计------------

{"passRate":1.0,"avgScore":1.0,"completionSuccessRate":1.0,...}
```

---

## 节点连接规则

`WorkflowBuilder.link()` 支持灵活的连接方式：

```java
// 单对单
builder.link(nodeA, nodeB);

// 单对多（广播，nodeA 执行完后，nodeB 和 nodeC 并行执行）
builder.link(nodeA, nodeB, nodeC);

// 多对单（汇聚，所有 scorers 执行完后才执行 counter）
builder.link(scorers, counter);

// 多对多
builder.link(scorers, reporters);

