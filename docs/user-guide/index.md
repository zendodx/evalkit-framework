# EvalKit Framework 用户指南

> 版本：1.2.x | 语言：Java 8+ | 构建：Maven 3.6+

## 文档目录

### 入门

| 文档 | 说明 |
|------|------|
| [概述与快速开始](./overview.md) | 框架简介、Maven 引入、整体架构、Hello World 示例 |
| [大模型服务（LLMService）](./llm-service.md) | 接入 LLM，配置模型参数、重试策略、Token 计费 |

### 数据准备

| 文档 | 说明 |
|------|------|
| [数据加载器（DataLoader）](./dataloader.md) | 从 Excel/CSV/JSON/JDBC/API 加载评测数据 |
| [数据装饰器（DataLoaderWrapper）](./dataloader-wrapper.md) | Mock 占位符替换、Prompt 润色、数据增强 |
| [Mock 规则引擎（Mocker）](./mocker.md) | 内置 6 种 Mocker 完整规则详解：精确日期、模糊日期、节假日、行政区划、景区 POI、数字；自定义 Mocker |
| [Query 生成器（QueryGenerator）](./querygen.md) | 生成测试 Query，支持 Mock 规则和 LLM 生成 |
| [数据生成器（DataGenerator）](./data-generator.md) | 自动生成多轮对话评测数据集，含知识图谱驱动方式（KGBased） |

### 评测核心

| 文档 | 说明 |
|------|------|
| [接口调用器（ApiCompletion）](./api-completion.md) | 调用被测业务接口，支持 HTTP、有序多轮调用 |
| [接口结果装饰器（ApiCompletionWrapper）](./api-completion-wrapper.md) | 在评估前对接口返回结果进行转化、清洗或 LLM 二次处理 |
| [评估器（Scorer）](./scorer.md) | 对接口返回结果进行打分，内置向量相似度、LLM 评分等多种策略 |
| [检查器（Checker）](./checker.md) | 细粒度规则检查，配合 MultiCheckerBasedScorer 使用 |

### 结果处理

| 文档 | 说明 |
|------|------|
| [统计器（Counter）](./counter.md) | 汇总评测指标，含 LLM 驱动的问题归因分析 |
| [上报器（Reporter）](./reporter.md) | 将评测结果输出到文件（Excel/CSV/JSON/HTML）、数据库或远程 API |

### 进阶使用

| 文档 | 说明 |
|------|------|
| [评测门面（EvalFacade）](./facade.md) | 全量评测、增量断点续评、有序增量评测三种模式详解 |
| [调试器（Debugger）](./debugger.md) | 注入已有数据跳过部分节点，加速开发调试和重新统计 |

---

## 框架核心概念

### 工作流（Workflow）

EvalKit 框架的核心是 **DAG 工作流**，将评测任务拆分为多个节点，通过工作流串联起来：

```
DataLoader → (DataLoaderWrapper) → ApiCompletion → Scorer
↓
Counter → Reporter
```

### DataItem（评测数据项）

每条评测数据在框架内以 `DataItem` 的形式流转，包含：

| 字段 | 说明 |
|---|---|
| `dataIndex` | 数据索引（编号） |
| `inputData` | 输入数据（原始评测数据 + 字段 Map） |
| `apiCompletionResult` | 接口调用结果 |
| `evalResult` | 评测结果（分数、是否通过、评分器结果列表） |
| `extra` | 额外扩展字段 |

### 快速选择指南

**我需要...**

- 加载 Excel/CSV 数据集 → [DataLoader](./dataloader.md)
- 自动生成测试数据 → [DataGenerator](./data-generator.md)
- 查询 Mock 占位符规则 → [Mocker](./mocker.md)
- 调用被测接口 → [ApiCompletion](./api-completion.md)
- 转化接口返回格式 → [ApiCompletionWrapper](./api-completion-wrapper.md)
- 打分评估接口质量 → [Scorer](./scorer.md)
- 细粒度检查多个条件 → [Checker](./checker.md)
- 统计通过率、错误归因 → [Counter](./counter.md)
- 保存/输出评测报告 → [Reporter](./reporter.md)
- 运行完整评测任务 → [EvalFacade](./facade.md)
- 快速调试，不想重跑接口 → [Debugger](./debugger.md)

