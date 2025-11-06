# ChangeLog

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