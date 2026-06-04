---
layout: default
title: 结果上报器（Reporter）
parent: 用户指南
nav_order: 13
has_toc: true
---

# 结果上报器（Reporter）

## 概述

评测完成后，所有的评测结果（每条数据的评分、统计汇总等）需要一个"出口"来输出或保存，这个"出口"就是**上报器（Reporter）**。

`Reporter` 是 EvalKit 框架工作流中的最后一个处理节点，负责将评测结果以各种形式输出：打印到控制台、写入文件（Excel/CSV/JSON/HTML）、存入数据库、或发送到远程 API。


## 类继承关系

```
Reporter（抽象基类）
├── StdReporter                  控制台打印
├── FileReporter（抽象）         文件型上报基类
│   ├── ExcelReporter            输出 Excel 文件
│   ├── CsvReporter              输出 CSV 文件
│   ├── JsonReporter             输出 JSON 文件
│   └── HtmlReporter             输出 HTML 可视化报告
├── JdbcReport（抽象）           数据库上报基类
└── ApiReporter（抽象）          HTTP API 上报基类
```


## 生命周期钩子

`Reporter` 提供了完整的生命周期钩子，允许你在上报前后插入自定义逻辑：

| 钩子方法 | 执行时机 | 默认行为 |
|---|---|---|
| `beforeReport(reportData)` | 上报前 | 空实现，可覆盖 |
| `report(reportData)` | 核心上报逻辑 | **抽象方法，必须实现** |
| `afterReport(reportData)` | 上报后 | 空实现，可覆盖 |
| `onErrorReport(reportData, e)` | 上报出错时 | 空实现，可覆盖 |

> ⚠️ 重要：上报过程中抛出的异常**不会阻塞**整个工作流的运行，框架会记录错误日志后继续执行。


## 上报数据结构（ReportData）

每次调用 `report()` 时，框架会把以下数据打包成 `ReportData` 传入：

| 字段 | 类型 | 说明 |
|---|---|---|
| `dataItems` | `List<DataItem>` | 所有评测数据项，包含输入、接口返回结果、评分等 |
| `countResultMap` | `Map<String, String>` | 各统计器的输出结果，key 为统计器名称，value 为 JSON 字符串 |


## 内置实现详解

### 1. StdReporter — 控制台打印

最简单的上报器，直接把结果打印到标准输出，适合本地调试时快速查看结果。

**输出格式：**

```
------------评测Case------------
{"dataIndex":0,"inputData":{...},"evalResult":{...}}
...
------------评测统计------------
------------BasicCounter------------
{"passCount":8,"failCount":2,"passRate":0.8}
```

**使用示例：**

```java
Workflow reportWorkflow = Workflow.builder()
    .addNode(new StdReporter())
    .addNode(new BasicCounter())
    .build();
```


### 2. ExcelReporter — Excel 文件上报

将评测结果输出为 `.xlsx` 格式的 Excel 文件。会生成两个文件：
- `{文件名}.xlsx`：每条评测数据的详情
- `{文件名}.count.xlsx`：统计汇总结果

**构造方法：**

```java
// 使用默认输出文件夹（attachments/）
new ExcelReporter("my_eval_report");

// 指定输出文件夹
new ExcelReporter("my_eval_report", "output/reports");
```

| 参数 | 说明 | 默认值 |
|---|---|---|
| `fileName` | 输出文件名（不含扩展名） | 当前时间字符串 |
| `parentDir` | 输出文件夹路径 | `attachments` |

**使用示例：**

```java
// 评测结果会保存到 attachments/my_eval_report.xlsx
ExcelReporter excelReporter = new ExcelReporter("my_eval_report");

Workflow reportWorkflow = Workflow.builder()
    .addNode(new BasicCounter())
    .addNode(excelReporter)
    .build();
```


### 3. CsvReporter — CSV 文件上报

将评测结果输出为 `.csv` 格式，与 Excel 类似，也会生成数据明细和统计两个文件。

**构造方法：**

```java
// 默认逗号分隔
new CsvReporter("my_eval_report");

// 指定分隔符（如制表符）
new CsvReporter("my_eval_report", "\t", "output/");
```

| 参数 | 说明 | 默认值 |
|---|---|---|
| `filename` | 输出文件名 | 当前时间字符串 |
| `delimiter` | 分隔符 | `,`（逗号） |
| `parentDir` | 输出文件夹路径 | `attachments` |


### 4. JsonReporter — JSON 文件上报

将评测结果输出为 `.json` 文件，输出格式如下：

```json
{
  "dataItems": [...],
  "countResult": {
    "BasicCounter": "{\"passCount\":8,...}"
  }
}
```

**构造方法：**

```java
new JsonReporter("my_eval_report");
new JsonReporter("my_eval_report", "output/json");
```

> 💡 `JsonReporter` 的输出格式与 `JsonFileDebugger` 的输入格式完全匹配，可以配合使用实现"生产数据→保存→调试重播"的工作流。


### 5. HtmlReporter — HTML 可视化报告

生成一个可在浏览器中打开的 HTML 格式可视化报告，是最直观的上报方式，适合展示和分享评测结果。

**支持两种风格：**

| 风格 | 常量 | 说明 |
|---|---|---|
| 默认风格 | `HtmlReportStyle.DEFAULT` | 标准报告样式 |
| GitHub 风格 | `HtmlReportStyle.GITHUB` | GitHub 风格样式 |

**构造方法：**

```java
// 默认风格
new HtmlReporter("my_eval_report");

// 指定风格
new HtmlReporter("my_eval_report", HtmlReportStyle.GITHUB);

// 指定文件夹和风格
new HtmlReporter("my_eval_report", "output/html", HtmlReportStyle.DEFAULT);
```

**使用示例：**

```java
HtmlReporter htmlReporter = new HtmlReporter("搜索质量评测_2024");
htmlReporter.setCdn("https://cdn.example.com"); // 可选：指定静态资源CDN

Workflow reportWorkflow = Workflow.builder()
    .addNode(new BasicCounter())
    .addNode(htmlReporter)
    .build();
```


### 6. JdbcReport — 数据库上报（抽象类）

将评测结果写入 MySQL 等关系型数据库，适合需要长期保存和查询历史数据的场景。

框架会自动建表（表不存在时），每次上报会使用一个唯一的 `group_id` 来标识本次评测批次。

**数据表结构：**

| 列名 | 类型 | 说明 |
|---|---|---|
| `id` | bigint | 自增主键 |
| `group_id` | varchar(100) | 本次上报的批次 ID（UUID） |
| `data_index` | bigint | 数据索引 |
| `input_data` | json | 输入数据 |
| `api_completion_result` | json | 接口调用结果 |
| `scorer_results` | json | 评分器结果 |
| `extra` | json | 额外数据 |

**使用示例（继承 `JdbcReport` 实现自定义上报）：**

```java
public class MyMysqlReporter extends JdbcReport {
    public MyMysqlReporter() {
        super(
            "com.mysql.cj.jdbc.Driver",            // 驱动类
            "jdbc:mysql://localhost:3306/eval_db",  // JDBC URL
            "root",                                // 用户名
            "password"                             // 密码
        );
    }

    @Override
    public String prepareTableName() {
        // 返回要写入的表名，可以动态生成
        return "eval_result_" + DateUtils.nowToString("yyyyMMdd");
    }
}
```


### 7. ApiReporter — HTTP API 上报（抽象类）

将每条评测结果逐条通过 HTTP 请求发送到自定义后台服务，适合接入自研的评测管理平台。

**需要实现的抽象方法：**

| 方法 | 说明 |
|---|---|
| `prepareBody(item)` | 构造请求体 Map |
| `prepareHeader(item)` | 构造请求头 Map |
| `prepareParams(item)` | 构造 URL 查询参数 |

**使用示例：**

```java
public class MyApiReporter extends ApiReporter {
    public MyApiReporter() {
        super(
            "https://my-eval-platform.com",  // host
            "/api/v1/eval/report",           // API路径
            "POST"                           // HTTP方法
        );
        // 可选：设置超时时间
        setTimeout(30, TimeUnit.SECONDS);
    }

    @Override
    public Map<String, Object> prepareBody(DataItem item) {
        Map<String, Object> body = new HashMap<>();
        body.put("dataIndex", item.getDataIndex());
        body.put("query", item.getInputData().getInputItem().get("query"));
        body.put("score", item.getEvalResult().getScore());
        body.put("isPassed", item.getEvalResult().isPassed());
        return body;
    }

    @Override
    public Map<String, String> prepareHeader(DataItem item) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer your-token");
        headers.put("Content-Type", "application/json");
        return headers;
    }

    @Override
    public Map<String, String[]> prepareParams(DataItem item) {
        return new HashMap<>(); // 无查询参数
    }
}
```


## 自定义上报器

如果内置的上报器无法满足需求，可以直接继承 `Reporter` 实现完全自定义的上报逻辑：

```java
public class MyCustomReporter extends Reporter {
    @Override
    protected void report(ReportData reportData) {
        List<DataItem> dataItems = reportData.getDataItems();
        Map<String, String> countResultMap = reportData.getCountResultMap();

        // 自定义处理逻辑，例如发送到飞书、钉钉、企业微信等
        for (DataItem item : dataItems) {
            // ...
        }
    }

    @Override
    protected void beforeReport(ReportData reportData) {
        // 上报前：例如初始化连接、打印开始日志
        System.out.println("开始上报，共 " + reportData.getDataItems().size() + " 条数据");
    }

    @Override
    protected void afterReport(ReportData reportData) {
        // 上报后：例如关闭连接、发送完成通知
        System.out.println("上报完成！");
    }

    @Override
    protected void onErrorReport(ReportData reportData, Throwable e) {
        // 上报出错：记录错误，发送告警
        System.err.println("上报失败: " + e.getMessage());
    }
}
```


## 多个上报器同时使用

在工作流中可以依次串联多个上报器，实现"同时输出到多个目标"：

```java
Workflow reportWorkflow = Workflow.builder()
    .addNode(new BasicCounter())          // 先统计
    .addNode(new StdReporter())           // 打印到控制台
    .addNode(new ExcelReporter("result")) // 保存 Excel
    .addNode(new HtmlReporter("result"))  // 生成 HTML 报告
    .build();
```


## 与统计器（Counter）配合使用

上报器只负责输出数据，统计汇总的工作由**统计器（Counter）**完成。统计器需要在上报器之前执行，才能把统计结果包含在输出文件里。

```java
// 正确顺序：先统计，再上报
Workflow reportWorkflow = Workflow.builder()
    .addNode(new BasicCounter())   // ① 先统计
    .addNode(new MetricCounter())  // ① 先统计
    .addNode(new ExcelReporter("result")) // ② 再上报（报告中会包含统计结果）
    .build();
```


## 文件输出路径说明

所有 `FileReporter` 子类（Excel、CSV、JSON、HTML）默认将文件输出到当前工作目录的 `attachments/` 文件夹下。如果文件夹不存在，框架会自动创建。

| 上报器 | 输出文件示例 |
|---|---|
| `ExcelReporter("result")` | `attachments/result.xlsx` 和 `attachments/result.count.xlsx` |
| `CsvReporter("result")` | `attachments/result.csv` 和 `attachments/result.count.csv` |
| `JsonReporter("result")` | `attachments/result.json` |
| `HtmlReporter("result")` | `attachments/result.html` |

