---
layout: default
title: Github Codecov用法参考
parent: 开发指南
nav_order: 91
---
Github Codecov使用方法

# 怎么从codecov获取token

从 Codecov 获取 Token 的步骤如下：

## 1. 登录 Codecov

访问 [https://codecov.io](https://codecov.io)，点击右上角 **Sign up** / **Log in**，选择 **Sign in with GitHub**，授权后跳回 Codecov 首页。

## 2. 添加你的仓库

登录后，左侧会列出你的 GitHub 组织/账号，找到 `zendodx` → 搜索 `evalkit-framework` → 点击 **Setup repo**（或 **Configure**）。

## 3. 获取 Token

进入仓库设置页面后，会看到如下内容：

```
Step 2: add the repository upload token as a secret
CODECOV_TOKEN = xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
```

复制这串 UUID 格式的 token。

## 4. 添加到 GitHub Secrets

1. 打开 GitHub 仓库页面：`https://github.com/zendodx/evalkit-framework`
2. 点击顶部 **Settings** 标签
3. 左侧菜单找到 **Secrets and variables → Actions**
4. 点击绿色按钮 **New repository secret**
5. 填写：
    - **Name**：`CODECOV_TOKEN`
    - **Secret**：粘贴刚才复制的 token
6. 点击 **Add secret** 保存

---

完成后，下次 push 代码触发 CI 时，GitHub Actions 就会自动将覆盖率数据上传到 Codecov，并在仓库页面展示覆盖率趋势图。

# 怎样在github开启代码覆盖率扫描

基于你的项目结构（Maven + Java + GitHub Actions），以下是开启代码覆盖率扫描的完整方案：

## 整体思路

需要做两件事：
1. **在 `pom.xml` 中添加 JaCoCo 插件** — 在 Maven 构建时生成覆盖率报告
2. **在 GitHub Actions 中添加 CI workflow** — 自动运行测试并上传覆盖率到 [Codecov](https://codecov.io)（免费、与 GitHub 集成好）

---

## 第一步：在根 `pom.xml` 添加 JaCoCo 插件

在 `pom.xml` 的 `<properties>` 中加入版本号，在 `<build><plugins>` 中加入 JaCoCo 插件：

```xml
 <!-- 代码覆盖率插件 -->
<plugin>
   <groupId>org.jacoco</groupId>
   <artifactId>jacoco-maven-plugin</artifactId>
   <version>${jacoco-maven-plugin.version}</version>
   <executions>
      <execution>
         <id>prepare-agent</id>
         <goals>
            <goal>prepare-agent</goal>
         </goals>
      </execution>
      <execution>
         <id>report</id>
         <phase>test</phase>
         <goals>
            <goal>report</goal>
         </goals>
      </execution>
   </executions>
</plugin>
```

## 第二步：创建 GitHub Actions CI 工作流

```yml
name: CI - Test & Coverage

on:
  push:
    branches:
      - main
      - master
      - 1.5.x
      - 1.4.x
  pull_request:
    branches:
      - main
      - master
      - 1.5.x
      - 1.4.x

jobs:
  test:
    name: Run Tests & Upload Coverage
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 8
        uses: actions/setup-java@v4
        with:
          java-version: '8'
          distribution: 'temurin'
          cache: maven

      - name: Run Tests with JaCoCo
        run: mvn -B test --no-transfer-progress -Dgpg.skip=true

      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v4
        with:
          token: ${{ secrets.CODECOV_TOKEN }}
          files: |
            evalkit-common/target/site/jacoco/jacoco.xml
            evalkit-workflow/target/site/jacoco/jacoco.xml
            evalkit-infra/target/site/jacoco/jacoco.xml
            evalkit-eval/target/site/jacoco/jacoco.xml
            evalkit-test/target/site/jacoco/jacoco.xml
          flags: unittests
          name: evalkit-coverage
          fail_ci_if_error: false
          verbose: true
```

## 第三步：在 GitHub 上配置 Codecov Token

Push 代码前，你还需要在 GitHub 仓库中添加 Secret：

1. 访问 [codecov.io](https://codecov.io)，用 GitHub 账号登录
2. 添加你的仓库 `zendodx/evalkit-framework`，获取 `CODECOV_TOKEN`
3. 在 GitHub 仓库页面进入 **Settings → Secrets and variables → Actions**
4. 点击 **New repository secret**，名称填 `CODECOV_TOKEN`，值填入从 Codecov 获取的 token

## 配置完成后的效果

| 功能 | 说明 |
|------|------|
| 本地覆盖率报告 | 执行 `mvn test` 后，在各模块的 `target/site/jacoco/index.html` 可查看 HTML 报告 |
| CI 自动扫描 | 每次 push 或 PR 自动运行测试，生成覆盖率并上传到 Codecov |
| README 徽章 | Codecov 提供徽章，可以贴到 `README.md` |

## 可选：在 README 中添加覆盖率徽章

```markdown
[![codecov](https://codecov.io/gh/zendodx/evalkit-framework/branch/main/graph/badge.svg?token=YOUR_TOKEN)](https://codecov.io/gh/zendodx/evalkit-framework)
```

---

**注意**：由于根 `pom.xml` 中 GPG 插件在 `verify` 阶段会签名，CI 里用了 `-Dgpg.skip=true` 跳过签名（只跑 `test` 阶段），否则会因为没有 GPG 私钥而失败。

# 怎么查看Jacoco的覆盖率

查看 Jacoco 覆盖率有以下几种方式：

## 方式一：直接打开 HTML 报告（最推荐）

每次运行 `mvn test` 后，Jacoco 会在各模块的 `target/site/jacoco/` 目录生成 HTML 报告。

先查看项目中是否已有报告：

报告已经生成了。直接用浏览器打开：

浏览器已打开报告。以下是所有查看方式的说明：

---

## 查看 Jacoco 覆盖率的几种方式

### 方式一：HTML 报告（已自动打开）

每次 `mvn test` 后，各模块都会生成可视化报告，路径规律为：

```
{模块名}/target/site/jacoco/index.html
```

本项目已生成的报告：
- `evalkit-eval/target/site/jacoco/index.html`
- `evalkit-infra/target/site/jacoco/index.html`
- `evalkit-workflow/target/site/jacoco/index.html`
- `evalkit-common/target/site/jacoco/index.html`

报告提供**行覆盖率**、**分支覆盖率**、**方法覆盖率**等多维度数据，可以点击包名 → 类名 → 方法名，逐层下钻查看具体哪些代码行被覆盖（绿色=覆盖，红色=未覆盖，黄色=部分覆盖）。

---

### 方式二：命令行查看汇总数据

```bash
# 查看某模块的文本覆盖率汇总（CSV 格式）
cat evalkit-eval/target/site/jacoco/jacoco.csv | head -5
```

---

### 方式三：手动重新生成报告

如果想刷新覆盖率数据（只生成报告，不重跑测试）：

```bash
# 重新跑测试并生成所有模块报告
mvn test --no-transfer-progress

# 仅针对某个模块
mvn test -pl evalkit-eval --no-transfer-progress
```

---

### 方式四：生成聚合报告（跨模块汇总）

如果项目配置了 `jacoco-report` 聚合模块，可以用：

```bash
mvn jacoco:report-aggregate
```

---

### 报告解读

HTML 报告的每列含义：

| 列名 | 含义 |
|------|------|
| **Instructions** | 字节码指令覆盖率（最精确） |
| **Branches** | 分支覆盖率（if/else/switch） |
| **Lines** | 源代码行覆盖率 |
| **Methods** | 方法覆盖率 |
| **Classes** | 类覆盖率 |

