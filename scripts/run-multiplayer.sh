#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

setup_linux_arm64_natives() {
    if [[ "$(uname -s)" != "Linux" || ! "$(uname -m)" =~ ^(aarch64|arm64)$ ]]; then
        return
    fi

    local native_dir="$ROOT/build/lwjgl-natives/linux-arm64"
    mkdir -p "$native_dir"

    local modules=(
        lwjgl
        lwjgl-freetype
        lwjgl-glfw
        lwjgl-jemalloc
        lwjgl-openal
        lwjgl-opengl
        lwjgl-stb
        lwjgl-tinyfd
    )

    for module in "${modules[@]}"; do
        local jar="$ROOT/build/lwjgl-natives/${module}-3.3.3-natives-linux-arm64.jar"
        if [[ ! -f "$jar" ]]; then
            curl -fsSL -o "$jar" "https://repo.maven.apache.org/maven2/org/lwjgl/${module}/3.3.3/${module}-3.3.3-natives-linux-arm64.jar"
        fi
        unzip -oq "$jar" '*.so' -d "$native_dir"
    done

    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dorg.lwjgl.librarypath=$native_dir -Djava.library.path=$native_dir"
    export CKD_GRADLE_ARGS=(--no-configuration-cache)
}

setup_linux_arm64_natives
mkdir -p "$ROOT/runs/multiplayer-logs"

cleanup() {
    jobs -pr | xargs -r kill
}
trap cleanup EXIT INT TERM

./gradlew runServer ${CKD_GRADLE_ARGS:-} > "$ROOT/runs/multiplayer-logs/server.log" 2>&1 &
server_pid=$!
sleep 12

./gradlew runClient ${CKD_GRADLE_ARGS:-} --args="--username CKD_Player_1" > "$ROOT/runs/multiplayer-logs/client-1.log" 2>&1 &
client_one_pid=$!
./gradlew runClient ${CKD_GRADLE_ARGS:-} --args="--username CKD_Player_2" > "$ROOT/runs/multiplayer-logs/client-2.log" 2>&1 &
client_two_pid=$!

echo "Started multiplayer test stack:"
echo "  Server PID:   $server_pid"
echo "  Client 1 PID: $client_one_pid"
echo "  Client 2 PID: $client_two_pid"
echo "Logs: runs/multiplayer-logs"
echo "Connect both clients to localhost. Press Ctrl+C here to stop spawned processes."

wait
