#!/bin/bash
# Start the Release Planning web app dev server

set -e

echo "==> Compiling wasmJs..."
./gradlew :webApp:wasmJsDevelopmentExecutableCompileSync --quiet

echo "==> Starting webpack dev server..."

NODE=~/.gradle/nodejs/node-v26.2.0-darwin-arm64/bin/node
TOOLING=~/.kotlin/kotlin-npm-tooling/yarn/9c7852378f220e63c4f92e7d93026c2f/node_modules
WEBPACK_BIN="$TOOLING/webpack/bin/webpack.js"
PKG_DIR="$(pwd)/build/wasm/packages/RepoDashboard-webApp"

# Kill any old server on port 8080
lsof -ti :8080 | xargs kill -9 2>/dev/null || true
sleep 1

NODE_PATH="$TOOLING" KOTLIN_TOOLING_DIR="$TOOLING" \
  "$NODE" "$WEBPACK_BIN" serve --config "$PKG_DIR/webpack.config.js" &
SERVER_PID=$!

echo "==> Waiting for server to start..."
sleep 4

echo "==> Opening http://localhost:8080/ in Chrome..."
open -a "Google Chrome" http://localhost:8080/ 2>/dev/null || open http://localhost:8080/

echo "==> Dev server running at http://localhost:8080/ (PID $SERVER_PID)"
echo "    Press Ctrl+C to stop."

wait $SERVER_PID
