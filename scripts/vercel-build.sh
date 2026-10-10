#!/bin/bash
# Builds the wasm web app on Vercel's build image, which ships without a JDK.
set -euo pipefail

JDK_DIR="$HOME/.jdk-21"
if [ ! -x "$JDK_DIR/bin/java" ]; then
  echo "==> Installing JDK 21 (Azul Zulu)..."
  mkdir -p "$JDK_DIR"
  META="https://api.azul.com/metadata/v1/zulu/packages/?java_version=21&os=linux&arch=x64&archive_type=tar.gz&java_package_type=jdk&javafx_bundled=false&crac_supported=false&latest=true&release_status=ga&availability_types=CA&page_size=1"
  URL=$(curl -fsSL "$META" | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>console.log(JSON.parse(s)[0].download_url))')
  echo "    $URL"
  curl -fsSL "$URL" | tar -xz -C "$JDK_DIR" --strip-components=1
fi
export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

echo "==> Building wasmJs production bundle..."
./gradlew :webApp:wasmJsBrowserDistribution --no-daemon --console=plain
