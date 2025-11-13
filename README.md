# <img src="docs/files/evalkit_logo.png" width="80" height="80"> EvalKit Framework

##### 📖 中文文档 | 📖 [English Documentation](README_en.md)

## 概述

EvalKit Framework是基于Java语言开发的自动化评测框架, 具有以下特性:

- 评测全流程编排: 可快速完成数据加载、评测执行、结果上报与输出、评测总结等全流程自动化处理。
- 评测编排多样化: 支持DAG工作流评测、全量评测与增量评测；增量模式下可实现断点续评，提升评测效率。
- 高扩展性: 除系统内置节点外，用户可灵活扩展并自定义评测节点，满足多样化业务需求。
- 高性能执行: 基于 DAG（有向无环图）实现多线程并行处理，显著提升执行效率。
- LLM集成支持: 提供LLM服务构建工厂,可快速对接大语言模型（LLM）服务，满足智能化评测需求。
- 轻量依赖: 框架依赖极少，不依赖 Spring，可便捷接入其他 Java 项目。

## 环境依赖

- JDK1.8+
- Maven3.6+

## Maven引入

```xml
<!-- https://mvnrepository.com/artifact/io.github.zendodx/evalkit-eval -->
<dependency>
    <groupId>io.github.zendodx</groupId>
    <artifactId>evalkit-eval</artifactId>
    <version>0.6.0</version>
</dependency>
```

## 相关文档

- [用户手册](docs/user-guide/user-guide.md)
- [版本变更](docs/changelog.md)
- [开发须知](docs/contribute.md)

## 开源协议

EvalKit Framework是在 [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) 协议下的开源项目