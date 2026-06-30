#!/usr/bin/env bash
set -e

usage() {
  echo "Usage: $0 [local|tag|central] [-skip-tests]"
  echo ""
  echo "  local    发布到本地 Maven 仓库（mvn install）"
  echo "  tag      通过 JitPack 发布：在当前 commit 打 Git Tag 并推送，JitPack 自动构建"
  echo "  central  发布到 Maven Central（需要 GPG 密钥和 Sonatype token，激活 release profile）"
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
    echo "🚀 发布到本地仓库..."
    mvn clean install -e $MVN_ARGS
    ;;
  tag)
    # 从 pom.xml 读取当前版本号
    VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null)
    TAG="v${VERSION}"
    echo "🏷️  JitPack 发布：打 Tag ${TAG} 并推送到 GitHub..."
    echo "   JitPack 将自动从 Tag 构建并提供依赖，无需手动 deploy。"
    echo ""
    # 检查 Tag 是否已存在
    if git rev-parse "$TAG" >/dev/null 2>&1; then
      echo "⚠️  Tag ${TAG} 已存在，跳过创建。"
    else
      git tag "$TAG"
      echo "✅ Tag ${TAG} 已创建"
    fi
    git push origin "$TAG"
    echo ""
    echo "✅ Tag 推送完成！"
    echo "   JitPack 构建地址：https://jitpack.io/#zendodx/evalkit-framework/${TAG}"
    echo ""
    echo "   用户接入方式（pom.xml）："
    echo "   <repositories>"
    echo "     <repository>"
    echo "       <id>jitpack.io</id>"
    echo "       <url>https://jitpack.io</url>"
    echo "     </repository>"
    echo "   </repositories>"
    echo "   <dependency>"
    echo "     <groupId>com.github.zendodx.evalkit-framework</groupId>"
    echo "     <artifactId>evalkit-eval</artifactId>"
    echo "     <version>${TAG}</version>"
    echo "   </dependency>"
    ;;
  central)
    echo "🚀 发布到 Maven 中央仓库（激活 release profile）..."
    mvn clean deploy -e -P release $MVN_ARGS
    ;;
  *)
    echo "❌ 无效参数: $ENV"
    usage
    ;;
esac

echo "✅ 完成：$ENV"
