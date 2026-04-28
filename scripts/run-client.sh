#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [[ "$(uname -s)" == "Linux" && "$(uname -m)" =~ ^(aarch64|arm64)$ ]]; then
    native_dir="$ROOT/build/lwjgl-natives/linux-arm64"
    mkdir -p "$native_dir"

    modules=(
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
        jar="$ROOT/build/lwjgl-natives/${module}-3.3.3-natives-linux-arm64.jar"
        if [[ ! -f "$jar" ]]; then
            curl -fsSL -o "$jar" "https://repo.maven.apache.org/maven2/org/lwjgl/${module}/3.3.3/${module}-3.3.3-natives-linux-arm64.jar"
        fi
        unzip -oq "$jar" '*.so' -d "$native_dir"
    done

    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dorg.lwjgl.librarypath=$native_dir -Djava.library.path=$native_dir"
    exec ./gradlew runClient --no-configuration-cache "$@"
fi

exec ./gradlew runClient "$@"