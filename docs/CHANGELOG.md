# ChangeLog

## [0.1.1] - 2025-10-09

### Added
- 增加是否开启断点续评开关, 默认开启
### Fixed
- 修复评测结果错误的bug
- 使用fastjson2替换jayway.jsonpath解析json路径
- 修复增量评测消息消费bug
- 修复html结果上报的未关闭writer的bug
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