#!/usr/bin/env bash
set -e

usage() {
  echo "Usage: $0 [local|remote] [-skip-tests]"
  exit 1
}

# 至少需要一个环境参数
[[ $# -lt 1 ]] && usage

ENV=$1
shift

# 默认值：不跳过测试
SKIP_TESTS=false

# 解析剩余参数
while [[ $# -gt 0 ]]; do
  case "$1" in
    -skip-tests)
      SKIP_TESTS=true
      ;;
    *)
      echo "❌ 未知参数：$1"
      usage
      ;;
  esac
  shift
done

# 构造 Maven 额外参数
MVN_ARGS=""
$SKIP_TESTS && MVN_ARGS="$MVN_ARGS -DskipTests"

case "$ENV" in
  local)
    echo "🚀 发布到本地仓库${SKIP_TESTS/+，已跳过测试/...}"
    mvn clean install -e $MVN_ARGS
    ;;
  remote)
    echo "🚀 发布到 Maven 中央仓库${SKIP_TESTS/+，已跳过测试/...}"
    # 排除 evalkit-test：该模块配置了 skipPublishing=true，而 central-publishing 插件
    # 只在 reactor 最后一个模块执行真正的 bundle 上传，若它排最后会导致整个发布被跳过
    mvn clean deploy -e $MVN_ARGS -pl '!evalkit-test'
    ;;
  *)
    echo "❌ 无效参数: $ENV"
    usage
    ;;
esac

echo "✅ 发布完成：$ENV"