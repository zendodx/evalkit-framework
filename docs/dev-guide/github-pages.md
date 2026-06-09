---
layout: default
title: Github Pages用法参考
parent: 开发指南
nav_order: 2
---

Github Pages使用方法

## 配置

(1) 在docs目录下创建Gemfile文件

```text
source "https://rubygems.org"

gem "just-the-docs"

group :jekyll_plugins do
  gem "jekyll-seo-tag"
end
```

## 本地调试流程

(1)进入docs目录
```shell
cd docs
```

(2)安装依赖
```shell
bundle install --path vendor/bundle
```

如果一直卡在安装步骤,需要配置国内镜像源

```text
# 使用腾讯云镜像
bundle config mirror.https://rubygems.org https://gems.ruby-china.com

# 或者直接修改 Gemfile，第一行改为：
source "https://gems.ruby-china.com"
```

(3) 执行预览

```shell
bundle exec jekyll serve --livereload
```

LiveReload address: http://127.0.0.1:35729

Server address: http://127.0.0.1:4000/evalkit-framework/

## Github Actions执行流程

(1) 增加文档配置文件

```text
.github/workflow/docs.yml
```


