---
layout: default
title: Github Pages用法参考
parent: 开发指南
nav_order: 90
---

# Github Pages使用方法

> Github Pages 是一个静态站点托管服务，可以用于搭建个人博客、文档站点等。

## 配置

(1) 在docs目录下创建Gemfile文件

```text
source "https://rubygems.org"

gem "just-the-docs"

group :jekyll_plugins do
  # _config.yml 中 remote_theme 配置所需
  gem "jekyll-remote-theme"
  gem "jekyll-seo-tag"
end
```

## 本地调试流程

(1)进入docs目录

```shell
cd docs
```

(2)安装依赖

> 注意：Bundler 2.1+ 已移除 `bundle install --path` 参数，需先用 `bundle config set` 指定安装路径。

```shell
# 指定依赖安装到本地 docs/vendor/bundle（--local 仅对当前目录生效，不污染全局配置）
bundle config set --local path vendor/bundle

# 安装依赖
bundle install
```

如果一直卡在安装步骤,需要配置国内镜像源

```shell
# 使用清华 TUNA 镜像（gems.ruby-china.com 已不可用）
bundle config set --local mirror.https://rubygems.org https://mirrors.tuna.tsinghua.edu.cn/rubygems/

# 或者直接修改 Gemfile，第一行改为：
# source "https://mirrors.tuna.tsinghua.edu.cn/rubygems/"
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


