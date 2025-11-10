# <img src="docs/files/evalkit_logo.png" width="80" height="80"> EvalKit Framework

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
    <version>0.5.2</version>
</dependency>
```

## User Documentation

- [User guide](docs/user-guide/user-guide.md)
- [Change log](docs/changelog.md)
- [Contribute](docs/contribute.md)

## Open Source License

EvalKit Framework is an open-source project licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

