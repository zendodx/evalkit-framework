---
layout: default
title: ChangeLog
nav_order: 4
---

# ChangeLog

## [1.4.0] - 2026-06-04

### Added

- Scorer支持条件执行配置condition,增加评估器路由,根据条件执行不同的Scorer
- api调用结果装饰器

### Fixed

- 增加大模型输出限制, 字符串中的""要用转义形式 \"\", 否则会造成反序列化错误
- extra中增加所选Scorer的数据
- RouterScorer路由匹配模式默认为匹配所有
- KG生成器增加场景和意图字段配置项
- 增加KG生成时的限制
- 重构html评测模版
- html评测报告支持渲染Rubric表格
- Rubric评估器支持配置单次评测指标数量
- 修复框架空指针问题

## [1.3.0] - 2026-06-02

### Added

- 支持基于量规Rubric的评估器

### Fixed

## [1.2.4] - 2026-06-01

### Added

### Fixed

- 修复增量评测无法结束的问题
- 优化附件存储目录
- 将文件名中的路径分隔符 "/" 替换为 "_"，保证文件名安全
- 优化评测配置构建器

## [1.2.3] - 2026-05-26

### Added

### Fixed

- 优化知识图谱数据生成器,prompt存到md文件, 不使用freemarker处理prompt
- 移除freemarker依赖
- 更新文档

## [1.2.2] - 2026-03-26

### Added

### Fixed

- 修改模糊日期Mocker

## [1.2.1] - 2026-03-19

### Added

### Fixed
- 中国模糊日期Mocker支持区分过去,未来日期
- html报告用例切换后仍然保持用例详情页位置
- 知识图谱生成器可同时配置多个配置文件

## [1.2.0] - 2026-03-18

### Added

- 增加中国模糊日期Mocker
- 增加数字Mocker
- 基于知识图谱的多轮对话Query生成器

### Fixed
- 序列化工具类增加json校验方法
- 修复类型转换工具类转换潜在问题
- SpelMockRuleEngine增加数字Mocker和中国模糊日期Mocker
- 为便于兼容和集成,修改DataGenerator父类为DataLoader
- 增加基于jene创建知识图谱的脚本
- 调整数据生成器结构

## [1.1.0] - 2026-03-12

### Added

- 评估器增加评估器类型字段scorerType
- 增加评估指标统计器
- html报告增加评估指标统计展示面板
- 增加类似github风格的html评测报告

### Fixed

- 重构html报告
- 修改html报告基础统计数据对象名称为basicCountResult

## [1.0.0] - 2026-01-04

### Added

- 增加框架性能测试模块
- 增加基于数据加载器的数据生成器,可基于原始数据进行生成

### Fixed

- 修改文件上报位置
- 修复增量评测线程泄漏和OOM问题
- 优化BatchRunner
- 修改数据生成器默认附件存储位置为attachments
- json文件加载器支持直接加载json数组

## [0.9.5] - 2025-12-22

### Added

### Fixed

- 修改ActiveMQServer初始端口随机化
- 修复顺序批量执行器执行顺序错乱问题
- 优化ActiveMQ端口获取方式
- 优化ActiveMQ和SQLite日志和异常处理
- 增量评测使用taskNameUuid替代taskName,解决任务名称不合法问题

## [0.9.4] - 2025-12-16

### Added

### Fixed

- 修复html报告名称显示错误的问题
- html报告补充case内容搜索范围

## [0.9.3] - 2025-12-12

### Added

### Fixed

- 修复归因构建LLM归因结果错误的bug

## [0.9.2] - 2025-12-1

### Added

### Fixed

- 修复数据注入时筛选异常的Bug
- 优化评测原因构建方式,支持文本,Json,大模型构建评估理由

## [0.9.1] - 2025-12-1

### Added

### Fixed

- 归因V2增加异常类别下拉选择框，选择后后边展示该类别下的问题数量

## [0.9.0] - 2025-11-28

### Added

- Json工具类支持读取classpath,远程,本地json文件
- 增加调试工具类
- 增加V2版本归因统计器,采用目录-问题二级结构

### Fixed

- 修复增量评测初始化失败时未关闭MQ和SQLite连接的问题
- 修改增量评测默认缓存位置
- 优化V1版本归因统计器
- html报告调整代码,拆分重构代码

## [0.8.0] - 2025-11-21

### Added

- 增加负载均衡LLM服务,缓解RPM限制问题

### Fixed

- 修复增量评测数据注入异常bug

## [0.7.0] - 2025-11-21

### Added

- 增加GSB评估器
- 增加答案相关性评估器
- 增加语义一致性评估器
- 增加解析失败重试机制

### Fixed

- 修改SecurityScorer的prompt
- 优化json提取
- 修改构造方法,补充配置参数校验
- 修复固定评估总分缺失bug

## [0.6.1] - 2025-11-14

### Added

- 增加数据加载器配置参数校验

### Fixed

- 调整打乱,筛选,截取的顺序

## [0.6.0] - 2025-11-13

### Added

- 增加大模型资源统计功能
- html报告支持多条件搜索
- html报告用例详情头部增加通过阈值
- html报告用例列表页增加筛选量展示
- html报告增加api结果搜索

### Fixed

- 修复MultiDataLoader加载null抛异常bug
- 修复ApiDataLoader配置失效bug
- LLMBasedChecker修改检查项理由
- 归因统计仅筛选有评测原因的用例

## [0.5.4] - 2025-11-11

### Added

### Fixed

- 归因统计仅筛选有评测原因的用例
- 修改LLMBasedChecker检查项理由

## [0.5.3] - 2025-11-10

### Added

### Fixed

- 修复html报告不能跳转bug,优化布局

## [0.5.2] - 2025-11-10

### Added

### Fixed

- 修复抽象大模型服务捕捉异常bug
- html报告取出电梯导航,详情页优化

## [0.5.1] - 2025-11-07

### Added

### Fixed

- html报告增加归因图表
- html报告问题归因功能优化,支持跳转用例详情
- html报告支持评测用例结果分享
- html报告用例详情增加电梯跳转
- LLM服务增加响应类型校验配置
- 增加mockito依赖

## [0.5.0] - 2025-11-06

### Added

- 增加类型转换工具类
- 增加Query生成器接口,评测用例数据生成器,基于mock的Query生成器
- 增加大模型服务配置参数, 支持大模型调用失败重试
- html报告页报告增加搜索栏
- html报告页支持url的dataIndex参数快速定位case

### Fixed

- mocker抽取成独立包,优化MockerEngine
- html报告页数组保留后2位
- html报告页修复归因页面展示bug

### Changed

## [0.4.4] - 2025-11-04

### Added

### Fixed

- 修复评估器打分错误bug
- 优化Scorer执行步骤
- 中国地区mock增加省份简称映射
- 过滤归因数据,仅归因存在评测原因的数据
- 优化前端UI

### Changed

## [0.4.2] - 2025-10-30

### Added

- Checker增加必过配置

### Fixed

- 优化csv,excel文件上报

### Changed
- 

## [0.4.1] - 2025-10-29

### Added

- DataLoader支持数据注入
- DateMocker支持范围内日期生成

### Fixed

- 修改csv,excel文件上报内容格式
- 修改PolishDataLoaderWrapper的sysPrompt
- 修改评测总分数和检查项总分数
- 修复JsonDataLoader未关联父配置的bug
- 修复FullEvalFacade获取剩余数据大小空指针bug

### Changed

- 更新用户手册

## [0.4.0] - 2025-10-22

### Added

- 支持评估器得分率卡控

### Fixed

- 修改节点配置式构造

### Changed

## [0.3.0] - 2025-10-22

### Added

- 增加顺序api调用器
- 增加顺序增量评测门面
- UuidUtils增加按key生成uuid方法

### Fixed

- 修改api调用器构造,改为配置方式

### Changed

## [0.1.2] - 2025-10-16

### Added

- 增加获取JVM布尔变量的静态方法
- 增加Json文件数据加载器
- 增加增量评测任务记录

### Fixed

- 修改基础统计指标,增加评测分数相关指标
- 删除html报告底部版权栏

### Changed

## [0.1.1] - 2025-10-09

### Added

- 增加是否开启断点续评开关, 默认开启
- 多数据加载器增加配置和分页构造方法

### Fixed

- 修复评测结果错误的bug
- 使用fastjson2替换jayway.jsonpath解析json路径
- 修复增量评测消息消费bug
- 修复html结果上报的未关闭writer的bug
- 修复文件上报时空指针bug
- 优化评测门面执行生命周期

### Changed

- pom统一版本号管理
- 添加flatten-maven-plugin插件,打包时自动展开变量

## [0.1.0] - 2025-10-08

### Added

- 增加全量评测和增量评测门面
- 增加多数据加载器
- 支持工作流深拷贝

### Fixed

- 优化html报告
- 弃用ThreadLocal管理上下文,改为变量传递

### Changed

- 删除无效单测
-

## [0.0.1] - 2025-09-27

### Added

- 定义基于DAG模型的通用工作流
- 定义评测工作流节点: Begin,DataLoader,DataLoaderWrapper,ApiCompletion,Scorer,Counter,Reporter,End,Debugger
- 支持全量数据评测

### Fixed

### Changed