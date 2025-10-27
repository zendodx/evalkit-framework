# 用户手册

# 概述

EvalKit-framework是基于Java的AI自动化评测框架, 具有以下特性:

- 评测全流程编排: 快速实现数据构造,评测执行,结果上报输出,评测总结流程
- 可扩展: 除了系统内置的节点外,可扩展自定义节点
- 高性能: 基于DAG实现,多线程执行DAG节点
- LLM: 支持快速集成LLM服务
- 依赖少: 依赖框架少,不依赖Spring,可快速接入其他Java工程
- 全量/增量评测: 支持全量数据评测和增量评测,增量评测还支持评测断点续评

# 环境

- JDK1.8+
- Maven3.x

# 引入依赖

其他版本可参考: [发版历史](CHANGELOG.md)

```xml

<dependency>
    <groupId>io.github.zendodx</groupId>
    <artifactId>evalkit-eval</artifactId>
    <version>0.3.0</version>
</dependency>
```

# 工作流节点定义

## 开始节点

### Begin

用于定义提供全局的配置

配置项:

| 配置项           | 说明                                                                | 必填 | 默认值                  |
|---------------|-------------------------------------------------------------------|----|----------------------|
| scoreStrategy | 打分策略, 框架支持2种打分策略:(1)求和策略 (2)最小值策略,用户可实现策略接口ScoreStrategy来实现自定义的策略 | 否  | 求和策略SumScoreStrategy |
| threshold     | 评测通过分数                                                            | 否  | 0.0                  |

用法:

```java
Begin begin = new Begin(
        BeginConfig.builder()
                .scoreStrategy(new SumScoreStrategy())
                .threshold(0.5)
                .build()
);
```

自定义打分策略用法:

```java
class CustomScoreStrategy implements ScoreStrategy {
    @Override
    public String getStrategyName() {
        // 定义策略名称
        return "自定义打分策略";
    }

    @Override
    public double calScore(List<ScorerResult> scorerResults) {
        // 计算打分的方式,scorerResults是每个评估器的结果
        return 0;
    }
}
```

## 数据加载器

### DataLoader

用于加载数据, 加载的数据会作为上下文传递给后续节点

配置项:

| 配置项     | 说明            | 必填 | 默认值   |
|---------|---------------|----|-------|
| offset  | 偏移量           | 否  | 0     |
| limit   | 数据数量,-1表示加载所有 | 否  | -1    |
| filters | 过滤器           | 否  | 无     |
| shuffle | 打乱加载顺序        | 否  | false |

框架中所有的数据加载器都基于DataLoader扩展, 用户也可通过继承DataLoader后实现自定义数据加载器.

用法:

```java
DataLoader dataLoader = new DataLoader(
        DataLoaderConfig.builder().build()
) {
    @Override
    public List<InputData> prepareDataList() throws Exception {
        return ListUtils.of(
                new InputData(MapUtils.of("query", "1")),
                new InputData(MapUtils.of("query", "2"))
        );
    }
};
```

### ExcelDataLoader

用于加载Excel数据,继承DataLoader

复用DataLoader的所有配置, 额外配置项如下:

| 配置项        | 说明                                          | 必填 | 默认值 |
|------------|---------------------------------------------|----|-----|
| filePath   | Excel文件路径,支持三种文件路径:(1)绝对路径 (2)类路径 (3)远程文件链接 | 是  | 无   |
| sheetIndex | 表格sheet页,从0开始                               | 是  | 无   |

用法:

```java
ExcelDataLoader excelDataLoader = new ExcelDataLoader(
        ExcelDataLoaderConfig.builder()
                .filePath("xxx")
                .sheetIndex(0)
                .build()
);
```

### JsonFileDataLoader

用于加载Json文件数据,继承DataLoader

复用DataLoader的所有配置, 额外配置项如下:

| 配置项      | 说明                                | 必填 | 默认值 |
|----------|-----------------------------------|----|-----|
| filePath | Json文件路径,(1)绝对路径 (2)类路径 (3)远程文件链接 | 是  | 无   |
| jsonPath | Json路径,默认取根节点数据                   | 是  | $   |

```java
JsonFileDataLoader jsonFileDataLoader = new JsonFileDataLoader(
        JsonFileDataLoaderConfig.builder()
                .filePath("xxx")
                .jsonPath("$.data")
                .build()
);
```

### ApiDataLoader

用于调用API接口加载数据,继承DataLoader

复用DataLoader的所有配置, 额外配置项如下:

| 配置项      | 说明            | 必填 | 默认值 |
|----------|---------------|----|-----|
| host     | 请求地址          | 是  | 无   |
| api      | 请求api         | 是  | 无   |
| method   | 请求方法          | 是  | 无   |
| timeout  | 请求超时时间, 默认120 | 是  | 无   |
| timeUnit | 请求超时时间单位, 默认秒 | 是  | 无   |

### 其他待补充

## 数据装饰器

### DataLoaderWrapper

用于对数据加载器进行装饰, 装饰后的数据加载器会作为上下文传递给后续节点

## 接口调用

### ApiCompletion

Api接口调用器,用于调用业务接口




## 评估器

## 结果整合器

## 结果上报器

## 调试器

## 结束节点