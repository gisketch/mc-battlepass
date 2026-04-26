#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPS="$ROOT/gradle.properties"

property_value() {
    local key="$1"
    sed -n "s/^${key}=//p" "$PROPS" | head -n 1
}

require_mod_id() {
    local value="$1"
    if [[ ! "$value" =~ ^[a-z0-9_]{2,64}$ ]]; then
        printf 'Invalid mod id: %s\nUse 2-64 lowercase letters, digits, underscores.\n' "$value" >&2
        exit 1
    fi
}

require_package() {
    local value="$1"
    if [[ ! "$value" =~ ^[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)+$ ]]; then
        printf 'Invalid package: %s\nUse Java/Kotlin package form, e.g. com.example.mymod.\n' "$value" >&2
        exit 1
    fi
}

replace_text() {
    local old="$1"
    local new="$2"
    shift 2
    OLD_TEXT="$old" NEW_TEXT="$new" perl -0pi -e 's/\Q$ENV{OLD_TEXT}\E/$ENV{NEW_TEXT}/g' "$@"
}

old_id="$(property_value mod_id)"
old_name="$(property_value mod_name)"
old_package="$(property_value mod_group_id)"

default_package="com.example.${old_id//_/}"

read -r -p "Mod ID [$old_id]: " mod_id
mod_id="${mod_id:-$old_id}"
require_mod_id "$mod_id"

read -r -p "Mod name [$old_name]: " mod_name
mod_name="${mod_name:-$old_name}"

read -r -p "Package/group [$default_package]: " package_name
package_name="${package_name:-$default_package}"
require_package "$package_name"

read -r -p "Icon path, PNG preferred [none]: " icon_path

mapfile -t text_files < <(
    find "$ROOT" \
        -path "$ROOT/.gradle" -prune -o \
        -path "$ROOT/build" -prune -o \
        -path "$ROOT/runs" -prune -o \
        -type f \
        \( -name '*.kt' -o -name '*.kts' -o -name '*.toml' -o -name '*.properties' -o -name '*.json' -o -name '*.md' -o -name '*.yaml' -o -name '*.sh' \) \
        -print
)

replace_text "$old_id" "$mod_id" "${text_files[@]}"
replace_text "$old_name" "$mod_name" "${text_files[@]}"
replace_text "$old_package" "$package_name" "${text_files[@]}"

old_assets="$ROOT/src/main/resources/assets/$old_id"
new_assets="$ROOT/src/main/resources/assets/$mod_id"
if [[ -d "$old_assets" && "$old_assets" != "$new_assets" ]]; then
    mkdir -p "$(dirname "$new_assets")"
    mv "$old_assets" "$new_assets"
fi

old_package_dir="$ROOT/src/main/kotlin/${old_package//./\/}"
new_package_dir="$ROOT/src/main/kotlin/${package_name//./\/}"
if [[ -d "$old_package_dir" && "$old_package_dir" != "$new_package_dir" ]]; then
    mkdir -p "$(dirname "$new_package_dir")"
    mv "$old_package_dir" "$new_package_dir"
fi

if [[ -n "$icon_path" ]]; then
    if [[ ! -f "$icon_path" ]]; then
        printf 'Icon not found: %s\n' "$icon_path" >&2
        exit 1
    fi
    mkdir -p "$ROOT/src/main/resources/assets/$mod_id"
    cp "$icon_path" "$ROOT/src/main/resources/assets/$mod_id/icon.png"
    perl -0pi -e 's/# logoFile="\$\{mod_icon_file\}"/logoFile="assets\/\$\{mod_id\}\/icon.png"/' "$ROOT/src/main/resources/META-INF/neoforge.mods.toml"
    perl -0pi -e 's/^mod_icon_file=.*/mod_icon_file=assets\/\$\{mod_id\}\/icon.png/m' "$PROPS"
fi

printf 'Updated starter: %s (%s) in %s\n' "$mod_name" "$mod_id" "$package_name"