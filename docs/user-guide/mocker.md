# Mock 规则引擎（Mocker）

## 概述

`MockDataLoaderWrapper` 通过**占位符替换**对数据进行增强，占位符格式统一为：

```
{{规则名 参数1 参数2 ...}}
```

框架内置 6 种 Mocker，全部在 `SpelMockRuleEngine` 中自动注册，**开箱即用**，无需额外配置。

---

## 内置 Mocker 总览

| Mocker 类名 | 触发规则名 | 说明 |
|---|---|---|
| `DateMocker` | 含 `date`（如 `date`、`future_date`、`past_date`） | 精确日期生成 |
| `ChinaFuzzyDateMocker` | `fuzzy_date`（精确匹配） | 口语化模糊日期 |
| `ChinaHolidayMocker` | 含 `holiday`（如 `holiday`、`future_holiday` 等） | 中国节假日/节气 |
| `ChinaAddressMocker` | `province` / `city` / `area` / `street`（精确匹配） | 中国行政区划 |
| `ChinaPoiMocker` | `scenic`（精确匹配） | 中国景区 POI |
| `NumberMocker` | 含 `int` 或 `float` | 随机数字 |

---

## 一、DateMocker — 精确日期

> 类名：`DateMocker`，触发条件：规则名包含 `date`（大小写不敏感，但不含 `fuzzy_date`）

### 1.1 当前时间 `{{date}}`

| 规则 | 说明 | 示例结果 |
|---|---|---|
| `{{date}}` | 当前时间，默认格式 `yyyy-MM-dd HH:mm:ss` | `2026-05-26 10:30:00` |
| `{{date yyyy/MM/dd}}` | 当前时间，自定义格式 | `2026/05/26` |
| `{{date HH:mm}}` | 当前时间，只取时分 | `10:30` |

### 1.2 未来日期 `{{future_date}}`

| 规则 | 说明 | 示例结果 |
|---|---|---|
| `{{future_date}}` | 未来 0~7 天内随机日期（默认） | `2026-05-29 10:30:00` |
| `{{future_date 14}}` | 未来 0~14 天内随机日期 | `2026-06-05 10:30:00` |
| `{{future_date 3 14}}` | 未来 3~14 天内随机日期 | `2026-06-03 10:30:00` |
| `{{future_date 14 yyyy-MM-dd}}` | 未来 0~14 天内，自定义格式 | `2026-06-05` |
| `{{future_date 3 14 yyyy/MM/dd}}` | 未来 3~14 天内，自定义格式 | `2026/06/03` |

### 1.3 过去日期 `{{past_date}}`

| 规则 | 说明 | 示例结果 |
|---|---|---|
| `{{past_date}}` | 过去 0~7 天内随机日期（默认） | `2026-05-20 10:30:00` |
| `{{past_date 30}}` | 过去 0~30 天内随机日期 | `2026-05-01 10:30:00` |
| `{{past_date 14 365}}` | 过去 14~365 天内随机日期 | `2026-01-15 10:30:00` |
| `{{past_date 7 yyyy-MM-dd}}` | 过去 0~7 天内，自定义格式 | `2026-05-20` |
| `{{past_date 14 365 yyyy/MM/dd}}` | 过去 14~365 天内，自定义格式 | `2026/01/15` |

### 1.4 参数解析规则

参数位置的含义由框架自动识别（纯数字 → 天数，含非数字字符 → 日期格式）：

| 参数数量 | 解析方式 |
|---|---|
| 无参数 | 使用默认范围（0~7天）+ 默认格式 |
| `[数字]` | 最多天数，默认格式 |
| `[格式字符串]` | 默认范围，自定义格式 |
| `[数字] [数字]` | 最少~最多天数，默认格式 |
| `[数字] [格式字符串]` | 最多天数，自定义格式 |
| `[数字] [数字] [格式字符串]` | 最少~最多天数，自定义格式 |

---

## 二、ChinaFuzzyDateMocker — 模糊日期

> 类名：`ChinaFuzzyDateMocker`，触发条件：规则名为 `fuzzy_date`（精确匹配）

适合生成口语化、自然语言风格的时间表达，如"下周"、"月底"、"明年"。

### 2.1 参数说明

```
{{fuzzy_date [类型] [方向]}}
```

- **类型**（可选）：`day`、`week`、`month`、`year`、`season`、`human`，不填表示随机所有类型
- **方向**（可选）：`future`（未来）、`past`（过去），不填表示随机混合

### 2.2 规则示例

| 规则 | 可能的结果 |
|---|---|
| `{{fuzzy_date}}` | `下周` / `月底` / `去年` / `近日` |
| `{{fuzzy_date future}}` | `明年` / `下周` / `月末` / `过两天` |
| `{{fuzzy_date past}}` | `上周` / `去年` / `前两三天` / `近日` |
| `{{fuzzy_date day future}}` | `不日` / `即日` / `改日` / `来日` / `当日` |
| `{{fuzzy_date day past}}` | `近日` / `近来` / `最近` / `日前` / `昔日` / `往日` |
| `{{fuzzy_date week future}}` | `本周` / `下周` / `周末` / `未来一周` / `未来二周` |
| `{{fuzzy_date week past}}` | `上周` / `上上周` / `大上周` / `过去一周` / `过去二周` |
| `{{fuzzy_date month future}}` | `月初` / `月中` / `月末` / `上旬` / `中旬` / `下旬` / `未来一月` |
| `{{fuzzy_date month past}}` | `上月` / `过去一月` / `过去二月` / `过去三月` |
| `{{fuzzy_date year future}}` | `今年` / `明年` / `后年` / `年初` / `年中` / `年底` / `下半年` / `来年` |
| `{{fuzzy_date year past}}` | `去年` / `前年` / `往年` / `往年同期` / `历年` / `上半年` |
| `{{fuzzy_date human future}}` | `过两天` / `等会儿` / `回头` / `赶明儿` |
| `{{fuzzy_date human past}}` | `前两三天` / `前几天` |

> `season` 类型：`春天` / `夏天` / `秋天` / `冬天` / `春节前后` / `暑假` 等季节相关表达，方向参数对 season 不生效。

---

## 三、ChinaHolidayMocker — 节假日

> 类名：`ChinaHolidayMocker`，触发条件：规则名包含 `holiday`（或 `solr_term_holiday`）

### 3.1 随机节假日（不限时间）

| 规则 | 说明 | 示例结果 |
|---|---|---|
| `{{holiday}}` | 随机公历或农历节假日 | `端午节` |
| `{{local_holiday}}` | 随机公历节假日 | `国庆节` |
| `{{chinese_holiday}}` | 随机农历节假日 | `腊八节` |
| `{{solr_term_holiday}}` | 随机二十四节气 | `清明` |

### 3.2 未来节假日

| 规则 | 说明 | 示例结果 |
|---|---|---|
| `{{future_holiday}}` | 当前时间之后的公历或农历节假日 | `中秋节` |
| `{{future_local_holiday}}` | 当前时间之后的公历节假日 | `国庆节` |
| `{{future_chinese_holiday}}` | 当前时间之后的农历节假日 | `七夕节` |
| `{{future_holiday 20261231}}` | 指定日期（`yyyyMMdd`）之后的节假日 | `春节` |
| `{{future_local_holiday 20260901}}` | 指定日期之后的公历节假日 | `国庆节` |
| `{{future_chinese_holiday 20260815}}` | 指定日期之后的农历节假日 | `中元节` |

### 3.3 过去节假日

| 规则 | 说明 |
|---|---|
| `{{past_holiday}}` | 当前时间之前的公历或农历节假日 |
| `{{past_local_holiday}}` | 当前时间之前的公历节假日 |
| `{{past_chinese_holiday}}` | 当前时间之前的农历节假日 |
| `{{past_holiday 20260101}}` | 指定日期（`yyyyMMdd`）之前的节假日 |
| `{{past_local_holiday 20260601}}` | 指定日期之前的公历节假日 |
| `{{past_chinese_holiday 20260601}}` | 指定日期之前的农历节假日 |

### 3.4 区间节假日

| 规则 | 说明 |
|---|---|
| `{{between_holiday 20260101 20261231}}` | 指定日期区间内的公历或农历节假日 |
| `{{between_local_holiday 20260101 20261231}}` | 指定区间内的公历节假日 |
| `{{between_chinese_holiday 20260101 20261231}}` | 指定区间内的农历节假日 |

> 日期参数格式均为 `yyyyMMdd`，如 `20261001`。

---

## 四、ChinaAddressMocker — 中国行政区划

> 类名：`ChinaAddressMocker`，触发条件：规则名为 `province` / `city` / `area` / `street`（精确匹配）

支持层级：**省 → 市 → 区/县 → 街道/乡镇**，不支持村级别。香港、澳门、台湾不支持街道级别。

### 4.1 省份 `{{province}}`

| 规则 | 说明 | 示例结果 |
|---|---|---|
| `{{province}}` | 随机省份（含自治区、直辖市、特别行政区） | `广东省` |

### 4.2 城市 `{{city}}`

| 规则 | 说明 | 示例结果 |
|---|---|---|
| `{{city}}` | 随机地级市 | `成都市` |
| `{{city 四川省}}` | 四川省下辖的随机城市 | `绵阳市` |
| `{{city 内蒙古}}` | 支持省份简称（自动转全称） | `呼和浩特市` |

### 4.3 区/县 `{{area}}`

| 规则 | 说明 | 示例结果 |
|---|---|---|
| `{{area}}` | 随机区/县 | `武侯区` |
| `{{area 四川省}}` | 四川省下辖的随机区/县 | `天府新区` |
| `{{area 四川省 成都市}}` | 成都市下辖的随机区/县 | `锦江区` |

### 4.4 街道/乡镇 `{{street}}`

| 规则 | 说明 | 示例结果 |
|---|---|---|
| `{{street}}` | 随机街道/乡镇 | `九眼桥街道` |
| `{{street 四川省}}` | 四川省下辖的随机街道 | `双桂街道` |
| `{{street 四川省 成都市}}` | 成都市下辖的随机街道 | `望江路街道` |
| `{{street 四川省 成都市 武侯区}}` | 武侯区下辖的随机街道 | `玉林街道` |

### 4.5 省份简称映射

以下简称会被**自动转换为全称**：

| 简称 | 全称 |
|---|---|
| `内蒙古` | `内蒙古自治区` |
| `广西` | `广西壮族自治区` |
| `宁夏` | `宁夏回族自治区` |
| `新疆` | `新疆维吾尔自治区` |
| `西藏` | `西藏自治区` |
| `香港` | `香港特别行政区` |
| `澳门` | `澳门特别行政区` |

---

## 五、ChinaPoiMocker — 中国景区 POI

> 类名：`ChinaPoiMocker`，触发条件：规则名为 `scenic`（精确匹配）

| 规则 | 说明 | 示例结果 |
|---|---|---|
| `{{scenic}}` | 随机中国景区 | `故宫博物院` |
| `{{scenic 四川省}}` | 四川省随机景区 | `九寨沟风景区` |
| `{{scenic 四川省 成都市}}` | 成都市随机景区 | `大熊猫繁育研究基地` |

---

## 六、NumberMocker — 数字

> 类名：`NumberMocker`，触发条件：规则名包含 `int` 或 `float`（大小写不敏感）

### 6.1 整数 `{{int}}`

默认范围：0~100。

| 规则 | 说明 | 示例结果 |
|---|---|---|
| `{{int}}` | 0~100 内随机整数（默认） | `42` |
| `{{int 10}}` | 10~100 内随机整数 | `73` |
| `{{int 1 10}}` | 1~10 内随机整数 | `7` |
| `{{int 100 999}}` | 100~999 内随机整数 | `528` |

### 6.2 小数 `{{float}}`

默认范围：0.0~100.0。

| 规则 | 说明 | 示例结果 |
|---|---|---|
| `{{float}}` | 0.0~100.0 内随机小数（默认） | `37.82` |
| `{{float 0.5}}` | 0.5~100.0 内随机小数 | `62.14` |
| `{{float 0.5 5.0}}` | 0.5~5.0 内随机小数 | `3.14` |
| `{{float 1.0 10.0}}` | 1.0~10.0 内随机小数 | `6.78` |

> **类型自动推断**：两个参数均为整数时输出整数，任一参数含小数点时输出小数。

---

## 七、综合使用示例

```
// 多种 Mocker 混合使用的 query 模板：
"{{future_date 3 14 yyyy-MM-dd}} 从 {{city 四川省}} 到 {{city 广东省}}，
帮我订 {{holiday}} 前后的机票，
价格 {{int 300 2000}} 元左右，{{fuzzy_date week future}}出发"

// 示例替换结果：
"2026-06-05 从 绵阳市 到 广州市，
帮我订 端午节 前后的机票，
价格 856 元左右，下周出发"
```

---

## 八、sameMock — 同名占位符控制

当同一条数据中出现**多个相同规则**的占位符时，通过 `sameMock` 控制是否生成相同值：

```java
MockDataLoaderWrapper mockWrapper = new MockDataLoaderWrapper(
    MockDataLoaderWrapperConfig.builder()
        .sameMock(false)  // false（默认）：每个占位符独立生成，可产生不同值
        // .sameMock(true)：相同规则的占位符生成同一个值
        .build()
) {
    @Override
    public List<String> selectMockFields() {
        return ListUtils.of("query");
    }
};
```

```
// sameMock=false（默认，推荐往返场景）：
// 输入：  "帮我订从{{city}}到{{city}}的机票"
// 输出：  "帮我订从北京到上海的机票"  ✓ 两个城市不同

// sameMock=true：
// 输出：  "帮我订从北京到北京的机票"  ✗ 两个城市相同（通常不合理）
```

---

## 九、自定义 Mocker

实现 `Mocker` 接口，在 `MockDataLoaderWrapper` 中注册即可：

```java
public class FlightMocker implements Mocker {

    @Override
    public boolean support(String ruleName, List<String> ruleParams) {
        return "flight".equals(ruleName);  // 响应 {{flight}} 和 {{flight 参数}} 规则
    }

    @Override
    public String mock(String ruleName, List<String> ruleParams) {
        // 生成随机航班号：CA1234、MU5678 等
        String[] airlines = {"CA", "MU", "CZ", "HU", "3U"};
        String airline = airlines[ThreadLocalRandom.current().nextInt(airlines.length)];
        int num = 1000 + ThreadLocalRandom.current().nextInt(9000);
        return airline + num;
    }
}
```

注册到 `MockDataLoaderWrapper`：

```java
MockDataLoaderWrapper mockWrapper = new MockDataLoaderWrapper() {
    {
        // 注册自定义 Mocker（在构造块中调用）
        engine.addMocker(new FlightMocker());
    }

    @Override
    public List<String> selectMockFields() {
        return ListUtils.of("query");
    }
};

// 使用：
// 输入：  "帮我查{{flight}}航班的剩余座位"
// 输出：  "帮我查CA7823航班的剩余座位"
```

也可以通过 `addMockers()` 批量注册多个自定义 Mocker：

```java
mockWrapper.addMockers(new FlightMocker(), new HotelBrandMocker(), new CuisineMocker());
```

---

## 十、快速规则速查

| 分类 | 规则 | 示例结果 |
|---|---|---|
| **精确日期** | `{{date}}` | `2026-05-26 10:30:00` |
| | `{{date yyyy-MM-dd}}` | `2026-05-26` |
| | `{{future_date 3 14}}` | `2026-06-05 10:30:00` |
| | `{{future_date 3 14 yyyy-MM-dd}}` | `2026-06-05` |
| | `{{past_date 14 365}}` | `2026-01-15 10:30:00` |
| **模糊日期** | `{{fuzzy_date}}` | `下周` |
| | `{{fuzzy_date week future}}` | `下周` / `周末` |
| | `{{fuzzy_date year past}}` | `去年` / `前年` |
| | `{{fuzzy_date human future}}` | `过两天` / `赶明儿` |
| **节假日** | `{{holiday}}` | `端午节` |
| | `{{future_holiday}}` | `中秋节` |
| | `{{between_holiday 20260101 20261231}}` | `元宵节` |
| | `{{solr_term_holiday}}` | `清明` |
| **行政区划** | `{{province}}` | `广东省` |
| | `{{city}}` / `{{city 四川省}}` | `成都市` |
| | `{{area 四川省 成都市}}` | `武侯区` |
| | `{{street 四川省 成都市 武侯区}}` | `玉林街道` |
| **景区** | `{{scenic}}` | `故宫博物院` |
| | `{{scenic 四川省 成都市}}` | `大熊猫繁育研究基地` |
| **数字** | `{{int 1 100}}` | `42` |
| | `{{float 0.5 5.0}}` | `3.14` |

