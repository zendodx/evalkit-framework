---
layout: default
title: 统计器（Counter）
parent: 用户指南
nav_order: 12
has_toc: true
---

# 统计器（Counter）

统计器在所有评估器执行完毕后运行，负责对所有数据的评测结果进行**汇总统计**，并将统计结果存入上下文，供 Reporter 使用。


## 体系结构

```
Counter（抽象基类）
├── BasicCounter         基础统计（通过率、耗时、分数分布等）
├── MetricCounter（抽象） 按指标名称分组统计
├── RubricCounter        Rubric 评估器专属统计（评估器级 + 维度级两级聚合）
├── AttributeCounter     LLM归因分析 V1（问题类型聚类）
└── AttributeCounterV2   LLM归因分析 V2（带类别、情感极性、置信度）
```


## BasicCounter

最常用的统计器，自动计算以下指标：

### 通过率指标

| 指标 | 说明 |
|------|------|
| `passRate` | 整体通过率 |
| `unPassRate` | 不通过率 |
| `totalCount` | 总数据量 |
| `passCount` | 通过条数 |
| `unPassCount` | 不通过条数 |
| `evalSuccessRate` | 评测成功率（评估器执行未报错） |
| `evalErrorRate` | 评测异常率 |
| `completionSuccessRate` | 接口调用成功率 |
| `completionErrorRate` | 接口调用失败率 |

### 接口耗时指标

| 指标 | 说明 |
|------|------|
| `completionAvgTimeCost` | 接口调用平均耗时（ms） |
| `completionMinTimeCost` | 最小耗时 |
| `completionMaxTimeCost` | 最大耗时 |
| `completionTP99TimeCost` | P99 耗时 |
| `completionTP95TimeCost` | P95 耗时 |
| `completionTP90TimeCost` | P90 耗时 |

### 评测分数指标

| 指标 | 说明 |
|------|------|
| `avgScore` | 平均分 |
| `minScore` | 最低分 |
| `maxScore` | 最高分 |
| `tp99Score` | P99 分数 |
| `tp95Score` | P95 分数 |
| `scoreStdDev` | 分数标准差 |
| `llmTokenCounts` | 各 LLM 模型的 Token 消耗和费用明细 |

### 用法

```java
BasicCounter counter = new BasicCounter();
```

就这一行！无需任何配置，把它放在所有 Scorer 之后即可。


## MetricCounter

按照**指标名称**分组统计，适合同时运行多个 Scorer 的场景，可以清晰地看到每个指标的通过率。

> `MetricCounter` 是抽象类，需实现 `buildMetricItems(List<DataItem>)` 方法，把 DataItem 转换成 `MetricItem` 列表。

### MetricItem 字段

| 字段 | 说明 |
|------|------|
| `metricName` | 指标名称（对应 `ScorerConfig.metricName`） |
| `metricValue` | 指标值（分数） |
| `metricThreshold` | 通过阈值 |

### 示例

```java
MetricCounter metricCounter = new MetricCounter() {
    @Override
    public List<MetricItem> buildMetricItems(List<DataItem> dataItems) {
        List<MetricItem> metricItems = new ArrayList<>();
        for (DataItem dataItem : dataItems) {
            if (dataItem.getEvalResult() == null) continue;
            // 为每个评估器的结果创建 MetricItem
            for (ScorerResult sr : dataItem.getEvalResult().getScorerResults()) {
                MetricItem item = new MetricItem();
                item.setMetricName(sr.getMetric());
                item.setMetricValue(sr.getScore());
                item.setMetricThreshold(sr.getThreshold());
                metricItems.add(item);
            }
        }
        return metricItems;
    }
};
```


## RubricCounter

专为 `RubricBasedScorer` 配套设计的统计器，按**评估器 → 维度**两级聚合，可以清晰地看到每个评估指标以及每个评估维度的通过情况和分数分布。

> `RubricCounter` 仅处理 `scorerType == "rubricBasedScorer"` 的结果，其他类型的 Scorer 结果会被自动忽略。

### 两级聚合结构

```
RubricCountResult
└── metricGroups（评估器级，按 metricName 分组）
    ├── metricName       评估器名称
    ├── totalCount       样本总数
    ├── passCount        通过数
    ├── failCount        失败数
    ├── passRate         通过率
    ├── failRate         失败率
    ├── avgScore         平均分（归一化后）
    ├── minScore         最低分
    ├── maxScore         最高分
    └── criteriaGroups（维度级，按 criteriaName 分组）
        ├── criteriaName   维度名称
        ├── avgRawScore    原始分均值
        ├── avgNormScore   归一化分均值
        ├── passThreshold  通过阈值（= passScore / maxScore）
        ├── passCount      达标样本数
        ├── failCount      未达标样本数
        ├── passRate       维度通过率
        ├── failRate       维度失败率
        └── dataPoints（各样本打分明细，用于报告层下钻）
            ├── dataIndex  样本序号
            ├── rawScore   原始分
            ├── normScore  归一化分
            ├── reason     打分理由
            └── passed     是否通过
```

### 用法

```java
RubricCounter rubricCounter = new RubricCounter();
```

同样只需一行，将其放在 `RubricBasedScorer` 之后即可。

### 典型工作流

```java
RubricBasedScorer rubricScorer = new RubricBasedScorer(...) { ... };
RubricCounter rubricCounter = new RubricCounter();
HtmlReporter reporter = new HtmlReporter("rubric_report");

new WorkflowBuilder()
    .link(begin, dataLoader, rubricScorer, rubricCounter, reporter)
    .build()
    .execute();
```

### 输出结果（RubricCountResult）

```json
{
  "metricGroups": [
    {
      "metricName": "内容质量",
      "totalCount": 100,
      "passCount": 82,
      "failCount": 18,
      "passRate": 0.82,
      "failRate": 0.18,
      "avgScore": 0.79,
      "minScore": 0.20,
      "maxScore": 1.00,
      "criteriaGroups": [
        {
          "criteriaName": "Faithfulness",
          "avgRawScore": 4.1,
          "avgNormScore": 0.85,
          "passThreshold": 0.6,
          "passCount": 91,
          "failCount": 9,
          "passRate": 0.91,
          "failRate": 0.09,
          "dataPoints": [
            {
              "dataIndex": 1,
              "rawScore": 5.0,
              "normScore": 1.0,
              "reason": "回答完全忠实于给定上下文",
              "passed": true
            }
          ]
        },
        {
          "criteriaName": "Harmfulness",
          "avgRawScore": 0.95,
          "avgNormScore": 0.95,
          "passThreshold": 1.0,
          "passCount": 95,
          "failCount": 5,
          "passRate": 0.95,
          "failRate": 0.05,
          "dataPoints": []
        }
      ]
    }
  ]
}
```

### 与 BasicCounter 组合使用

`RubricCounter` 专注于 Rubric 维度的精细化统计，而 `BasicCounter` 提供整体通过率和耗时等基础指标，两者可以组合使用，互补不冲突：

```java
BasicCounter basicCounter = new BasicCounter();
RubricCounter rubricCounter = new RubricCounter();
HtmlReporter reporter = new HtmlReporter("rubric_full_report");

new WorkflowBuilder()
    .link(rubricScorer, basicCounter)
    .link(basicCounter, rubricCounter)
    .link(rubricCounter, reporter)
    .build()
    .execute();
```


## AttributeCounter（归因分析 V1）

用 LLM 对**不通过的评测案例**进行问题归因，自动分析哪些类型的问题最常出现，帮助你快速定位 AI 服务的短板。

### 工作原理

1. 收集所有数据的 `evalResult.reason`（评测失败理由）
2. 将失败理由分批发给 LLM，让它归纳问题类型
3. 对相似的问题类型进行同义词合并
4. 按问题出现频次从高到低排序，输出归因报告

### 用法

```java
// 需要提供一个 LLM 服务
AttributeCounter attributeCounter = new AttributeCounter(myLLMService);
```

### 输出结果（AttributeCountResult）

```json
{
  "overallAttribution": [
    {
      "issueName": "位置信息不准确",
      "caseIds": [1, 5, 12, 23, 45]
    },
    {
      "issueName": "时间理解错误",
      "caseIds": [3, 8, 17]
    },
    {
      "issueName": "无法处理多目的地查询",
      "caseIds": [6, 9]
    }
  ]
}
```


## AttributeCounterV2（归因分析 V2）

在 V1 基础上增加了**根因分类**、**情感极性**和**置信度**维度，输出更结构化的归因报告。

### 新增维度

| 维度 | 说明 |
|------|------|
| `category`（根因类别） | 问题所属的大类（如"位置识别"、"时间推理"） |
| `issue`（具体问题） | 具体的问题描述 |
| `confidence`（置信度） | 0~1，LLM 判断的确定程度 |
| `sentiment`（情感极性） | `NEG`（负面问题）/ `NEUTRAL`（中性）|
| `representative`（代表描述） | LLM 对该类问题的 50 字摘要 |

### 用法

```java
AttributeCounterV2 attributeCounterV2 = new AttributeCounterV2(myLLMService);
```

### 输出结果（AttributeCountResultV2）

```json
{
  "categories": [
    {
      "categoryName": "位置识别",
      "categoryCode": "wei_zhi_shi_bie",
      "issues": [
        {
          "issueName": "混淆同名景区",
          "issueCode": "hun_xiao_tong_ming_jing_qu",
          "confidence": 0.92,
          "sentiment": "NEG",
          "representative": "用户询问某城市的某景区时，AI返回了另一个同名景区的信息",
          "caseIds": [1, 5, 12]
        }
      ]
    },
    {
      "categoryName": "日期推理",
      "issues": [
        {
          "issueName": "无法正确处理'下周'等相对日期",
          "confidence": 0.88,
          "sentiment": "NEG",
          "caseIds": [3, 8]
        }
      ]
    }
  ]
}
```


## 自定义统计器

继承 `Counter` 基类，实现 `count(List<DataItem>)` 方法：

```java
Counter customCounter = new Counter() {
    @Override
    protected CountResult count(List<DataItem> dataItems) {
        // 统计某个自定义指标，例如：回复平均长度
        double avgLen = dataItems.stream()
                .filter(d -> d.getApiCompletionResult() != null)
                .filter(d -> d.getApiCompletionResult().isSuccess())
                .mapToInt(d -> {
                    String resp = d.getApiCompletionResult().get("response");
                    return resp == null ? 0 : resp.length();
                })
                .average()
                .orElse(0.0);

        // 返回统计结果，实现 CountResult 接口或继承 BasicCountResult
        BasicCountResult result = new BasicCountResult();
        // 可以把自定义数据放到 extra 字段中
        result.setExtra(MapUtils.of("avgResponseLength", avgLen));
        return result;
    }
};
```


## 典型用法：组合多个统计器

在工作流中可以串联多个统计器，每个统计器的结果会被合并到最终统计：

```java
BasicCounter basicCounter = new BasicCounter();
AttributeCounterV2 attributeCounter = new AttributeCounterV2(myLLMService);
HtmlReporter reporter = new HtmlReporter("eval_report");

new WorkflowBuilder()
    .link(scorers, basicCounter)
    .link(basicCounter, attributeCounter)   // basicCounter 先统计，再做归因
    .link(attributeCounter, reporter)
    .build()
    .execute();

