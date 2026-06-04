---
layout: default
title: 数据装饰器（DataLoaderWrapper）
parent: 用户指南
nav_order: 4
has_toc: true
---

# 数据装饰器（DataLoaderWrapper）

数据装饰器用于在数据加载后、接口调用前，对测试数据进行**增强或变换**。典型用途包括：

- **Mock 数据替换**：把数据中的占位符（格式：`双花括号包裹规则名和参数`，如 `holiday`、`city 四川省`）替换成真实随机值
- **LLM 润色**：用大模型把原始 query 改写成更自然、口语化的表达
- **自定义增强**：在每条数据上追加字段、调用外部接口补充信息等

## 体系结构

```
DataLoaderWrapper（抽象基类）
├── MockDataLoaderWrapper         替换 占位符 为随机值
├── PromptDataLoaderWrapper（抽象） 用 LLM Prompt 增强数据
│   └── PolishDataLoaderWrapper（抽象） 专门做口语润色
└── （自定义继承 DataLoaderWrapper）
```

## DataLoaderWrapper（基类）

最通用的装饰器基类，实现 `wrapper(DataItem)` 方法即可。

### 配置项

| 配置项 | 说明 | 必填 | 默认值 |
|--------|------|------|--------|
| `threadNum` | 并发处理线程数 | 否 | 1 |
| `llmService` | 大模型服务（需要调用 LLM 的子类使用） | 否 | null |

### 示例：追加字段

```java
DataLoaderWrapper wrapper = new DataLoaderWrapper(
        DataLoaderWrapperConfig.builder()
                .threadNum(4)  // 4 线程并发处理
                .build()
) {
    @Override
    protected void wrapper(DataItem dataItem) {
        // 在每条数据上追加一个 timestamp 字段
        dataItem.getInputData().put("timestamp", String.valueOf(System.currentTimeMillis()));
    }
};
```

## MockDataLoaderWrapper

用于替换数据中的**占位符**，框架内置了多种 Mock 数据生成规则，详细规则见 [mocker.md](./mocker)。

### 配置项

包含 `DataLoaderWrapper` 所有配置，额外配置项：

| 配置项 | 说明 | 必填 | 默认值 |
|--------|------|------|--------|
| `sameMock` | 同一条数据中，多个相同规则标记的占位符是否 Mock 为同一个值 | 否 | false |
| `fillEmptyStringOnMockFail` | Mock 失败时填充空字符串（而非抛错） | 否 | false |

> 实现 `selectMockFields()` 方法，指定需要替换占位符的字段列表。

### 示例

```java
MockDataLoaderWrapper mockWrapper = new MockDataLoaderWrapper() {
    @Override
    public List<String> selectMockFields() {
        // 只对这些字段做 Mock 替换
        return ListUtils.of("query");
    }
};
```

```
// 数据中 query 字段的值：
"future_date 3 7从北京去city，帮我订holiday特价机票"
// 替换后示例结果：
"2026-06-03从北京去广州，帮我订端午节特价机票"
```

## 内置 Mock 规则详解

### DateMocker（日期）

| 规则 | 说明 | 示例 |
|------|------|------|
| `date` | 当前时间（默认格式 `yyyy-MM-dd HH:mm:ss`） | `2026-05-26 10:30:00` |
| `date yyyy/MM/dd` | 当前时间（自定义格式） | `2026/05/26` |
| `future_date 7` | 未来 0~7 天内随机日期 | `2026-05-29` |
| `future_date 3 14` | 未来 3~14 天内随机日期 | `2026-06-03` |
| `future_date 3 14 yyyy/MM/dd` | 未来 3~14 天内随机日期（自定义格式） | `2026/06/03` |
| `past_date 7` | 过去 0~7 天内随机日期 | `2026-05-20` |
| `past_date 14 365` | 过去 14~365 天内随机日期 | `2026-01-15` |

### HolidayMocker（节假日）

| 规则 | 说明 |
|------|------|
| `holiday` | 随机返回一个公历或农历节假日名称 |
| `local_holiday` | 随机公历节假日（元旦、春节、清明…） |
| `chinese_holiday` | 随机农历节假日 |
| `future_holiday` | 将来的某个节假日 |
| `future_holiday 20250815` | 2025-08-15 之后的节假日 |
| `past_holiday` | 过去的某个节假日 |
| `between_holiday 20250101 20251231` | 2025 年内的某个节假日 |

### ChinaAddressMocker（中国行政区划）

| 规则 | 说明 | 示例 |
|------|------|------|
| `province` | 随机省份 | `广东省` |
| `city` | 随机地级市 | `成都市` |
| `city 四川省` | 四川省下辖某个市 | `绵阳市` |
| `area` | 随机区/县 | `天府新区` |
| `area 四川省 成都市` | 成都市下辖某个区 | `武侯区` |
| `street` | 随机街道/乡镇 | `九眼桥街道` |
| `street 四川省 成都市 武侯区` | 武侯区下辖某个街道 | `望江路街道` |

### ChinaPOIMocker（景区）

| 规则 | 说明 | 示例 |
|------|------|------|
| `scenic` | 随机景区名称 | `故宫博物院` |
| `scenic 四川省` | 四川省某景区 | `九寨沟风景区` |
| `scenic 四川省 成都市` | 成都市某景区 | `大熊猫繁育研究基地` |

### NumberMocker（数字）

| 规则 | 说明 | 示例 |
|------|------|------|
| `int 1 10` | 1~10 之间的随机整数 | `7` |
| `float 0.5 5.0` | 0.5~5.0 之间的随机小数 | `3.14` |

## PromptDataLoaderWrapper

用 LLM Prompt 对数据进行增强，是一个**抽象类**，需要实现 `preparePrompt()` 和 `selectField()` 方法。

### 示例：用 LLM 补充标准答案

```java
PromptDataLoaderWrapper promptWrapper = new PromptDataLoaderWrapper(
        DataLoaderWrapperConfig.builder()
                .llmService(myLLMService)
                .threadNum(4)
                .build()
) {
    @Override
    public String preparePrompt() {
        // 告诉 LLM 要做什么
        return "你是一位旅游专家，请根据用户的问题给出简洁的标准答案（100字以内）：";
    }

    @Override
    public String selectField() {
        // 指定要处理的字段，处理结果会覆盖写回该字段
        return "groundTruth";
    }
};
```

## PolishDataLoaderWrapper

专门用于把 query 字段改写成**更自然、口语化**的表达，继承自 `PromptDataLoaderWrapper`。

### 配置项

包含 `DataLoaderWrapper` 所有配置，额外配置项：

| 配置项 | 说明 | 必填 | 默认值 |
|--------|------|------|--------|
| `sysPrompt` | 大模型系统提示词 | 否 | 内置口语化改写提示词 |
| `style` | 润色风格描述 | 否 | `清晰明确` |
| `splitChar` | 多结果分隔符（如果 LLM 返回多条结果） | 否 | 空（不分割） |

> 实现 `selectField()` 方法指定要润色的字段名。

### 示例

```java
PolishDataLoaderWrapper polishWrapper = new PolishDataLoaderWrapper(
        PolishDataLoaderWrapperConfig.builder()
                .llmService(myLLMService)
                .style("口语化，像普通用户在手机上打字")
                .build()
) {
    @Override
    public String selectField() {
        return "query";  // 对 query 字段进行润色
    }
};
```

```
// 原始 query：
"帮我订一张明天从北京到上海的高铁票"

// 润色后示例：
"我明天要去上海出差，能帮我查下从北京出发的高铁，最好早上出发的"
```

## 自定义 Mock 规则

如果内置的 Mock 规则不满足需求，可以实现 `Mocker` 接口自定义规则：

```java
public class FlightNumberMocker implements Mocker {

    @Override
    public boolean support(String ruleName, List<String> ruleParams) {
        // 声明此 Mocker 处理 "flight" 规则
        return "flight".equals(ruleName);
    }

    @Override
    public String mock(String ruleName, List<String> ruleParams) {
        // 生成随机航班号，如 CA1234、MU5678
        String[] airlines = {"CA", "MU", "CZ", "HU", "3U"};
        String airline = airlines[ThreadLocalRandom.current().nextInt(airlines.length)];
        int flightNum = 1000 + ThreadLocalRandom.current().nextInt(9000);
        return airline + flightNum;
    }
}
```

然后在 `MockDataLoaderWrapper` 中注册：

```java
MockDataLoaderWrapper mockWrapper = new MockDataLoaderWrapper() {
    {
        // 注册自定义 Mocker
        engine.registerMocker(new FlightNumberMocker());
    }

    @Override
    public List<String> selectMockFields() {
        return ListUtils.of("query");
    }
};

// 使用示例：
// 输入：  "帮我查flight航班的剩余座位"
// 输出：  "帮我查CA7823航班的剩余座位"
```

## 同名占位符 sameMock

当一条数据中出现多个**相同规则**的占位符时，可以控制它们是生成**不同值**还是**同一个值**：

```
// 场景：往返机票，出发地和目的地的城市不能相同
// query = "帮我订从city到city的机票"

// sameMock=false（默认）：两个 city 分别独立 Mock，可能生成两个不同城市
// 输出：  "帮我订从北京到上海的机票"  ✓

// sameMock=true：两个 city 生成同一个城市
// 输出：  "帮我订从北京到北京的机票"  ✗（不合理）
```

所以在需要**不同值**时，保持 `sameMock=false`（默认）即可。

