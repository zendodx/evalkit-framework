# <img src="docs/files/evalkit_logo.png" width="80" height="80"> EvalKit Framework

[![Maven Central](https://img.shields.io/maven-central/v/io.github.zendodx/evalkit-eval?color=blue&logo=apache-maven)](https://mvnrepository.com/artifact/io.github.zendodx/evalkit-eval)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/Java-8%2B-orange?logo=openjdk)](https://www.oracle.com/java/)
[![codecov](https://codecov.io/gh/zendodx/evalkit-framework/branch/master/graph/badge.svg)](https://codecov.io/gh/zendodx/evalkit-framework)
[![GitHub Stars](https://img.shields.io/github/stars/zendodx/evalkit-framework?style=social)](https://github.com/zendodx/evalkit-framework/stargazers)
[![GitHub Forks](https://img.shields.io/github/forks/zendodx/evalkit-framework?style=social)](https://github.com/zendodx/evalkit-framework/forks)

##### 📖 English Documentation | 📖 [中文文档](README.md)

## Overview

EvalKit Framework is an automated evaluation framework developed in Java, offering the following key features:

- End-to-End Evaluation Workflow: Enables rapid automation of data construction, evaluation execution, result reporting and output, as well as evaluation summary generation. 
- Full / Incremental Evaluation: Supports both full data evaluation and incremental evaluation; the incremental mode allows checkpoint-based resumption for improved efficiency. 
- High Extensibility: In addition to built-in nodes, users can easily extend and define custom evaluation nodes to meet diverse business needs. 
- High Performance Execution: Implements DAG (Directed Acyclic Graph) with multi-threaded parallel processing to significantly boost execution speed. 
- LLM Integration Support: Provides quick integration with Large Language Model (LLM) services to enable intelligent evaluation capabilities. 
- Lightweight Dependencies: Requires minimal dependencies, does not rely on Spring, and can be seamlessly integrated into other Java projects.

## Tool Preparation

- JDK1.8+
- Maven3.6+

## Add Dependencies

```xml
<!-- https://mvnrepository.com/artifact/io.github.zendodx/evalkit-eval -->
<dependency>
    <groupId>io.github.zendodx</groupId>
    <artifactId>evalkit-eval</artifactId>
    <version>${evalkit-eval.version}</version>
</dependency>
```

## User Documentation

- [User guide](docs/user-guide/summary.md)
- [Change log](docs/changelog.md)
- [Contribute](docs/contribute.md)

## Open Source License

EvalKit Framework is an open-source project licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

