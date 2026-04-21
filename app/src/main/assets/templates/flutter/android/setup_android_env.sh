#!/usr/bin/env bash
set -e

log() {
  echo "[flutter-android-setup] $*" >&2
}

fail() {
  log "ERROR: $*"
  exit 1
}

command_exists() {
  command -v "$1" >/dev/null 2>&1
}

speed_to_int() {
  local speed="$1"
  if [[ ! "$speed" =~ ^[0-9]+(\.[0-9]+)?$ ]]; then
    echo 0
    return
  fi
  local speed_int="${speed%.*}"
  if [[ -z "$speed_int" ]]; then
    speed_int=0
  fi
  echo "$speed_int"
}

GRADLE_VERSION="8.14"
GRADLE_ROOT="${GRADLE_ROOT:-$HOME/gradle}"
GRADLE_DIST="gradle-${GRADLE_VERSION}"
GRADLE_ZIP="${GRADLE_ROOT}/${GRADLE_DIST}-bin.zip"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
FLUTTER_RELEASES_URL="${FLUTTER_RELEASES_URL:-}"
FLUTTER_DEFAULT_INSTALL_DIR="${FLUTTER_DEFAULT_INSTALL_DIR:-$HOME/flutter}"
ANDROID_NDK_VERSION="${ANDROID_NDK_VERSION:-28.2.13676358}"
ANDROID_NDK_ZIP_URL="${ANDROID_NDK_ZIP_URL:-}"
ANDROID_CMAKE_VERSION="${ANDROID_CMAKE_VERSION:-3.22.1}"
ENABLE_ARM64_NDK_EMULATION="${ENABLE_ARM64_NDK_EMULATION:-1}"
FLUTTER_STORAGE_BASE_URL_SELECTED=""
PUB_HOSTED_URL_SELECTED=""
FLUTTER_SDK=""
SCRIPT_DIR=""
export GRADLE_USER_HOME
APT_UPDATED=0

ping_host() {
  local host="$1"
  local ping_cmd=""
  if command_exists ping; then
    ping_cmd="ping"
  elif command_exists busybox; then
    ping_cmd="busybox ping"
  fi
  if [[ -z "$ping_cmd" ]]; then
    log "ping not found; skipping mirror checks"
    return 2
  fi
  if $ping_cmd -c 1 -W 2 "$host" >/dev/null 2>&1; then
    log "Ping OK: $host"
    return 0
  fi
  log "Ping fail: $host"
  return 1
}

select_download_url() {
  local label="$1"
  local default_url="$2"
  local default_host="$3"
  shift 3
  local mirror_args=("$@")

  log "Selecting fastest mirror for $label"

  if command_exists curl; then
    local best_url="$default_url"
    local best_speed=0

    local probe_dir
    probe_dir=$(mktemp -d)

    {
      local speed
      speed=$(measure_download_speed "$default_url") || speed="0"
      printf '%s\t%s\n' "$(speed_to_int "$speed")" "$default_url" > "$probe_dir/default"
    } &

    local i=0
    local probe_idx=0
    while (( i < ${#mirror_args[@]} )); do
      local url="${mirror_args[$((i + 1))]}"
      local key="mirror_${probe_idx}"
      {
        local speed
        speed=$(measure_download_speed "$url") || speed="0"
        printf '%s\t%s\n' "$(speed_to_int "$speed")" "$url" > "$probe_dir/$key"
      } &
      i=$((i + 2))
      probe_idx=$((probe_idx + 1))
    done

    wait

    local result_file
    while IFS= read -r -d '' result_file; do
      local speed_int
      speed_int=$(cut -f1 "$result_file")
      local url
      url=$(cut -f2- "$result_file")
      if [[ -n "$speed_int" && "$speed_int" -gt "$best_speed" ]]; then
        best_speed="$speed_int"
        best_url="$url"
      fi
    done < <(find "$probe_dir" -type f -print0)

    rm -rf "$probe_dir"

    if [[ "$best_speed" -gt 0 ]]; then
      log "Fastest mirror selected for $label: $best_url (speed=${best_speed}B/s)"
      echo "$best_url"
      return 0
    fi

    log "Speed test failed for all mirrors; fallback to ping selection"
  fi

  if ping_host "$default_host"; then
    echo "$default_url"
    return 0
  fi

  local i=0
  while (( i < ${#mirror_args[@]} )); do
    local host="${mirror_args[$i]}"
    local url="${mirror_args[$((i + 1))]}"
    i=$((i + 2))
    if ping_host "$host"; then
      log "Using mirror for $label: $host"
      echo "$url"
      return 0
    fi
  done

  log "No reachable mirror; fallback to default URL for $label"
  echo "$default_url"
}

measure_download_speed() {
  local url="$1"
  # Download a small range to /dev/null and use curl's measured speed.
  # Use short timeouts to keep selection fast.
  curl -L \
    --range 0-524287 \
    --output /dev/null \
    --silent \
    --show-error \
    --connect-timeout 3 \
    --max-time 8 \
    -w "%{speed_download}" \
    "$url" 2>/dev/null
}

download_file() {
  local url="$1"
  local dest="$2"
  local max_retries=3
  local retry_count=0

  while [[ $retry_count -lt $max_retries ]]; do
    if command_exists aria2c; then
      if aria2c \
        --allow-overwrite=true \
        --continue=true \
        --max-connection-per-server=16 \
        --split=16 \
        --min-split-size=1M \
        --connect-timeout=30 \
        --timeout=120 \
        --max-tries=3 \
        --retry-wait=3 \
        --async-dns=false \
        --disable-ipv6=true \
        --file-allocation=none \
        --summary-interval=0 \
        --console-log-level=warn \
        --out="$(basename "$dest")" \
        --dir="$(dirname "$dest")" \
        "$url"; then
        return 0
      fi
    elif command_exists curl; then
      if curl -L --connect-timeout 30 --max-time 120 --retry 2 --retry-delay 3 "$url" -o "$dest"; then
        return 0
      fi
    elif command_exists wget; then
      if wget --timeout=30 --tries=3 --waitretry=3 -O "$dest" "$url"; then
        return 0
      fi
    else
      log "aria2c/curl/wget is required to download files."
      exit 1
    fi

    retry_count=$((retry_count + 1))
    log "Download failed, retrying ($retry_count/$max_retries)..."
    sleep 2
  done

  log "Failed to download file after $max_retries attempts: $url"
  return 1
}

download_text() {
  local url="$1"
  if command_exists curl; then
    curl -L --connect-timeout 30 --max-time 120 --retry 2 --retry-delay 3 --silent --show-error "$url"
    return
  fi
  if command_exists wget; then
    wget --timeout=30 --tries=3 --waitretry=3 -qO- "$url"
    return
  fi
  fail "curl or wget is required to download text resources."
}

install_packages() {
  local packages=("$@")
  if command_exists apt-get; then
    local sudo_cmd=""
    if command_exists sudo; then
      sudo_cmd="sudo"
    fi
    log "Installing packages: ${packages[*]}"
    if [[ "$APT_UPDATED" -eq 0 ]]; then
      $sudo_cmd apt-get update
      APT_UPDATED=1
    fi
    $sudo_cmd apt-get install -y "${packages[@]}"
  else
    log "apt-get not found; please install: ${packages[*]}"
  fi
}

detect_flutter_arch() {
  case "$(uname -m)" in
    x86_64 | amd64)
      echo "x64"
      ;;
    aarch64 | arm64 | arm64_v8a)
      echo "arm64"
      ;;
    *)
      echo "arm64"
      ;;
  esac
}

select_flutter_release_manifest_url() {
  if [[ -n "$FLUTTER_RELEASES_URL" ]]; then
    echo "$FLUTTER_RELEASES_URL"
    return
  fi

  select_download_url \
    "Flutter release manifest" \
    "https://storage.googleapis.com/flutter_infra_release/releases/releases_linux.json" \
    "storage.googleapis.com" \
    "storage.flutter-io.cn" "https://storage.flutter-io.cn/flutter_infra_release/releases/releases_linux.json"
}

resolve_flutter_storage_base_url() {
  if [[ -n "$FLUTTER_STORAGE_BASE_URL_SELECTED" ]]; then
    echo "$FLUTTER_STORAGE_BASE_URL_SELECTED"
    return
  fi

  if [[ -n "${FLUTTER_STORAGE_BASE_URL:-}" ]]; then
    FLUTTER_STORAGE_BASE_URL_SELECTED="${FLUTTER_STORAGE_BASE_URL%/}"
    echo "$FLUTTER_STORAGE_BASE_URL_SELECTED"
    return
  fi

  local manifest_url
  manifest_url=$(select_flutter_release_manifest_url)
  local derived_root="${manifest_url%/flutter_infra_release/releases/releases_linux.json}"
  if [[ "$derived_root" == "$manifest_url" || -z "$derived_root" ]]; then
    derived_root="https://storage.googleapis.com"
  fi

  FLUTTER_STORAGE_BASE_URL_SELECTED="$derived_root"
  echo "$FLUTTER_STORAGE_BASE_URL_SELECTED"
}

select_flutter_release_base_url() {
  local storage_root
  storage_root=$(resolve_flutter_storage_base_url)
  echo "${storage_root%/}/flutter_infra_release/releases"
}

parse_flutter_release_manifest() {
  local manifest="$1"
  local stable_hash
  stable_hash=$(printf '%s\n' "$manifest" | sed -n 's/.*"stable": "\([^"]*\)".*/\1/p' | head -n 1)
  if [[ -z "$stable_hash" ]]; then
    return 1
  fi

  local base_url
  base_url=$(printf '%s\n' "$manifest" | sed -n 's/.*"base_url": "\([^"]*\)".*/\1/p' | head -n 1)
  if [[ -z "$base_url" ]]; then
    return 1
  fi

  local release_info
  release_info=$(printf '%s\n' "$manifest" | awk -v hash="$stable_hash" '
    index($0, "\"hash\": \"" hash "\"") { found = 1 }
    found && /"version":/ {
      line = $0
      sub(/^.*"version": "/, "", line)
      sub(/".*$/, "", line)
      version = line
    }
    found && /"archive":/ {
      line = $0
      sub(/^.*"archive": "/, "", line)
      sub(/".*$/, "", line)
      archive = line
    }
    found && /"release_date":/ {
      line = $0
      sub(/^.*"release_date": "/, "", line)
      sub(/".*$/, "", line)
      release_date = line
    }
    found && /"sha256":/ {
      line = $0
      sub(/^.*"sha256": "/, "", line)
      sub(/".*$/, "", line)
      sha = line
      print hash "\t" version "\t" archive "\t" sha "\t" release_date
      exit
    }
  ')
  if [[ -z "$release_info" ]]; then
    return 1
  fi

  printf '%s\t%s\n' "$base_url" "$release_info"
}

install_flutter_from_archive() {
  local install_dir="$1"
  install_packages xz-utils

  if ! command_exists tar; then
    fail "tar is required to extract the Flutter SDK archive."
  fi
  if ! command_exists sha256sum; then
    fail "sha256sum is required to verify the Flutter SDK archive."
  fi

  log "Fetching Flutter stable release manifest"
  local manifest
  local manifest_url
  manifest_url=$(select_flutter_release_manifest_url)
  manifest=$(download_text "$manifest_url") || fail "Failed to download Flutter release manifest: $manifest_url"

  local release_info
  release_info=$(parse_flutter_release_manifest "$manifest") || fail "Failed to parse Flutter stable release manifest."

  local base_url
  base_url=$(printf '%s' "$release_info" | cut -f1)
  local release_hash
  release_hash=$(printf '%s' "$release_info" | cut -f2)
  local version
  version=$(printf '%s' "$release_info" | cut -f3)
  local archive
  archive=$(printf '%s' "$release_info" | cut -f4)
  local expected_sha256
  expected_sha256=$(printf '%s' "$release_info" | cut -f5)

  if [[ -z "$base_url" || -z "$release_hash" || -z "$version" || -z "$archive" || -z "$expected_sha256" ]]; then
    fail "Flutter stable release metadata is incomplete."
  fi

  local download_base_url
  download_base_url=$(select_flutter_release_base_url)
  if [[ -z "$download_base_url" ]]; then
    download_base_url="$base_url"
  fi
  local archive_url="${download_base_url%/}/$archive"

  log "Installing Flutter stable $version from $archive_url"

  local tmp_dir
  tmp_dir=$(mktemp -d)
  local archive_path="$tmp_dir/flutter-sdk.tar.xz"
  download_file "$archive_url" "$archive_path"

  local actual_sha256
  actual_sha256=$(sha256sum "$archive_path" | awk '{print $1}')
  if [[ "$actual_sha256" != "$expected_sha256" ]]; then
    rm -rf "$tmp_dir"
    fail "Flutter SDK checksum mismatch: expected $expected_sha256, got $actual_sha256"
  fi

  mkdir -p "$(dirname "$install_dir")"
  tar -xJf "$archive_path" -C "$tmp_dir"

  if [[ ! -d "$tmp_dir/flutter" ]]; then
    rm -rf "$tmp_dir"
    fail "Flutter archive did not contain the expected flutter directory."
  fi

  rm -rf "$install_dir"
  mv "$tmp_dir/flutter" "$install_dir"
  rm -rf "$tmp_dir"
}

install_flutter_sdk() {
  local install_dir="$FLUTTER_DEFAULT_INSTALL_DIR"
  local arch
  arch=$(detect_flutter_arch)

  if [[ -x "$install_dir/bin/flutter" ]]; then
    FLUTTER_SDK="$install_dir"
    return
  fi

  log "Flutter SDK not found; installing it automatically"
  case "$arch" in
    x64 | arm64)
      if [[ "$arch" == "arm64" ]]; then
        log "Using the official generic Linux Flutter SDK bundle; Flutter will bootstrap Linux arm64 host tools from official storage"
      fi
      install_flutter_from_archive "$install_dir"
      ;;
    *)
      fail "Unsupported Linux host architecture: $arch"
      ;;
  esac

  FLUTTER_SDK="$install_dir"
}

ensure_ping() {
  if command_exists ping || command_exists busybox; then
    return
  fi
  if command_exists apt-get; then
    install_packages iputils-ping
  fi
  if ! command_exists ping && ! command_exists busybox; then
    log "ping still unavailable; mirror selection will be skipped"
  fi
}

ensure_java() {
  if command_exists java; then
    local version
    version=$(java -version 2>&1 | sed -n 's/.*version "\(.*\)".*/\1/p')
    local major=${version%%.*}
    if [[ "$major" == "1" ]]; then
      major=$(echo "$version" | cut -d. -f2)
    fi
    if [[ -n "$major" && "$major" -ge 17 ]]; then
      log "Java $version detected"
      return
    fi
    log "Java version $version is below 17; upgrading"
  else
    log "Java not found; installing OpenJDK 17"
  fi
  install_packages openjdk-17-jdk
}

resolve_java_home() {
  if [[ -n "${JAVA_HOME:-}" && -d "$JAVA_HOME" ]]; then
    return
  fi
  if command_exists java; then
    local java_path
    java_path=$(readlink -f "$(command -v java)")
    JAVA_HOME=$(dirname "$(dirname "$java_path")")
    export JAVA_HOME
  fi
}

resolve_flutter_sdk() {
  if [[ -n "${FLUTTER_ROOT:-}" && -d "$FLUTTER_ROOT" ]]; then
    FLUTTER_SDK="$FLUTTER_ROOT"
  elif [[ -n "${FLUTTER_HOME:-}" && -d "$FLUTTER_HOME" ]]; then
    FLUTTER_SDK="$FLUTTER_HOME"
  elif [[ -f "local.properties" ]]; then
    local configured_flutter_sdk
    configured_flutter_sdk=$(sed -n 's/^flutter\.sdk=//p' local.properties | tail -n 1)
    if [[ -n "$configured_flutter_sdk" && -d "$configured_flutter_sdk" ]]; then
      FLUTTER_SDK="$configured_flutter_sdk"
    fi
  fi

  if [[ -z "$FLUTTER_SDK" ]] && command_exists flutter; then
    local flutter_path
    flutter_path=$(readlink -f "$(command -v flutter)")
    FLUTTER_SDK=$(dirname "$(dirname "$flutter_path")")
  fi

  if [[ -z "$FLUTTER_SDK" || ! -d "$FLUTTER_SDK" || ! -x "$FLUTTER_SDK/bin/flutter" ]]; then
    install_flutter_sdk
  fi

  export FLUTTER_SDK
  export FLUTTER_ROOT="$FLUTTER_SDK"
  export PATH="$FLUTTER_SDK/bin:$PATH"
  log "Flutter SDK detected: $FLUTTER_SDK"
}

configure_flutter_storage_env() {
  local storage_root
  storage_root=$(resolve_flutter_storage_base_url)
  export FLUTTER_STORAGE_BASE_URL="$storage_root"
  log "Using Flutter storage mirror: $FLUTTER_STORAGE_BASE_URL"
}

measure_pub_host_speed() {
  local pub_url="$1"
  local probe_url="${pub_url%/}/api/packages/flutter"
  if ! command_exists curl; then
    echo 0
    return 0
  fi
  local speed
  speed=$(curl -L \
    --output /dev/null \
    --silent \
    --show-error \
    --connect-timeout 3 \
    --max-time 8 \
    -w "%{speed_download}" \
    "$probe_url" 2>/dev/null || echo 0)
  speed_to_int "$speed"
}

configure_pub_hosted_url() {
  if [[ -n "${PUB_HOSTED_URL:-}" ]]; then
    PUB_HOSTED_URL_SELECTED="${PUB_HOSTED_URL%/}"
    export PUB_HOSTED_URL="$PUB_HOSTED_URL_SELECTED"
    log "Using user-specified PUB_HOSTED_URL: $PUB_HOSTED_URL"
    return 0
  fi

  local default_pub="https://pub.dev"
  local mirror_pub="https://pub.flutter-io.cn"

  local default_speed mirror_speed
  default_speed=$(measure_pub_host_speed "$default_pub")
  mirror_speed=$(measure_pub_host_speed "$mirror_pub")

  if [[ "$mirror_speed" -gt "$default_speed" ]]; then
    PUB_HOSTED_URL_SELECTED="$mirror_pub"
  else
    PUB_HOSTED_URL_SELECTED="$default_pub"
  fi

  export PUB_HOSTED_URL="$PUB_HOSTED_URL_SELECTED"
  log "Selected PUB_HOSTED_URL: $PUB_HOSTED_URL (pub.dev=${default_speed}B/s, mirror=${mirror_speed}B/s)"
}

bootstrap_flutter_sdk() {
  log "Bootstrapping Flutter SDK"
  if ! flutter --version; then
    fail "Flutter SDK bootstrap failed."
  fi
}
precache_flutter_sdk() {
  log "Precaching Flutter artifacts for Linux and Android"
  if ! flutter precache --linux --android; then
    fail "Flutter artifact precache failed."
  fi
}

is_arm64_host() {
  case "$(uname -m)" in
    aarch64 | arm64 | arm64_v8a)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

ensure_flutter_arm64_dart_sdk() {
  if ! is_arm64_host; then
    return 0
  fi

  local dart_bin="$FLUTTER_SDK/bin/cache/dart-sdk/bin/dart"
  if [[ -x "$dart_bin" ]] && "$dart_bin" --version >/dev/null 2>&1; then
    log "ARM64 host Dart SDK is already usable"
    return 0
  fi

  local engine_version_file="$FLUTTER_SDK/bin/internal/engine.version"
  if [[ ! -f "$engine_version_file" ]]; then
    fail "Flutter engine.version not found: $engine_version_file"
  fi

  local engine_version
  engine_version=$(tr -d '[:space:]' < "$engine_version_file")
  if [[ -z "$engine_version" ]]; then
    fail "Failed to read Flutter engine version from: $engine_version_file"
  fi

  local storage_root
  storage_root=$(resolve_flutter_storage_base_url)
  local primary_url="${storage_root%/}/flutter_infra_release/flutter/${engine_version}/dart-sdk-linux-arm64.zip"
  local fallback_url="https://storage.googleapis.com/flutter_infra_release/flutter/${engine_version}/dart-sdk-linux-arm64.zip"

  log "ARM64 host detected; repairing Flutter Dart SDK cache"

  local tmp_dir
  tmp_dir=$(mktemp -d)
  local zip_path="$tmp_dir/dart-sdk-linux-arm64.zip"

  if ! download_file "$primary_url" "$zip_path"; then
    log "Primary Dart SDK URL failed, trying fallback: $fallback_url"
    download_file "$fallback_url" "$zip_path" || {
      rm -rf "$tmp_dir"
      fail "Failed to download ARM64 Dart SDK for engine $engine_version"
    }
  fi

  local extract_dir="$tmp_dir/extract"
  mkdir -p "$extract_dir"
  unzip -q "$zip_path" -d "$extract_dir"

  local extracted_sdk_dir=""
  if [[ -d "$extract_dir/dart-sdk" ]]; then
    extracted_sdk_dir="$extract_dir/dart-sdk"
  else
    extracted_sdk_dir=$(find "$extract_dir" -mindepth 1 -maxdepth 3 -type d -name dart-sdk | head -n 1)
  fi

  if [[ -z "$extracted_sdk_dir" || ! -d "$extracted_sdk_dir" ]]; then
    rm -rf "$tmp_dir"
    fail "Extracted ARM64 Dart SDK directory not found"
  fi

  rm -rf "$FLUTTER_SDK/bin/cache/dart-sdk"
  mkdir -p "$FLUTTER_SDK/bin/cache"
  mv "$extracted_sdk_dir" "$FLUTTER_SDK/bin/cache/dart-sdk"

  chmod +x "$FLUTTER_SDK/bin/cache/dart-sdk/bin/dart" 2>/dev/null || true
  chmod +x "$FLUTTER_SDK/bin/cache/dart-sdk/bin/dartaotruntime" 2>/dev/null || true

  rm -f "$FLUTTER_SDK/bin/cache/flutter_tools.snapshot"
  rm -rf "$tmp_dir"

  if ! "$FLUTTER_SDK/bin/cache/dart-sdk/bin/dart" --version >/dev/null 2>&1; then
    fail "ARM64 Dart SDK verification failed after patch"
  fi

  log "ARM64 Dart SDK patch applied successfully"
}

verify_setup_health() {
  log "Running final health checks"
  flutter --version || fail "flutter --version failed during final check"
  if ! flutter doctor -v; then
    log "flutter doctor reports remaining optional issues; setup continues"
  fi
}

ensure_android_ndk_preinstall() {
  local ndk_dir="$ANDROID_HOME/ndk/$ANDROID_NDK_VERSION"
  if [[ -x "$ndk_dir/ndk-build" ]]; then
    log "Android NDK already present: $ANDROID_NDK_VERSION"
    return 0
  fi

  install_packages unzip

  local ndk_url="${ANDROID_NDK_ZIP_URL:-}"
  if [[ -z "$ndk_url" ]]; then
    case "$ANDROID_NDK_VERSION" in
      28.2.13676358)
        ndk_url="https://dl.google.com/android/repository/android-ndk-r28b-linux.zip"
        ;;
      *)
        log "No direct NDK zip mapping for $ANDROID_NDK_VERSION; will fallback to sdkmanager"
        return 1
        ;;
    esac
  fi

  local selected_ndk_url
  selected_ndk_url=$(select_download_url \
    "Android NDK ${ANDROID_NDK_VERSION}" \
    "$ndk_url" \
    "dl.google.com" \
    "mirrors.tuna.tsinghua.edu.cn" "https://mirrors.tuna.tsinghua.edu.cn/android/repository/$(basename "$ndk_url")" \
    "mirrors.bfsu.edu.cn" "https://mirrors.bfsu.edu.cn/android/repository/$(basename "$ndk_url")" \
    "mirrors.aliyun.com" "https://mirrors.aliyun.com/android/repository/$(basename "$ndk_url")")

  local tmp_dir
  tmp_dir=$(mktemp -d)
  local ndk_zip="$tmp_dir/ndk.zip"

  log "Pre-downloading Android NDK with multi-connection downloader"
  if ! download_file "$selected_ndk_url" "$ndk_zip"; then
    rm -rf "$tmp_dir"
    log "NDK zip download failed; will fallback to sdkmanager"
    return 1
  fi

  local extract_dir="$tmp_dir/extract"
  mkdir -p "$extract_dir"
  if ! unzip -q "$ndk_zip" -d "$extract_dir"; then
    rm -rf "$tmp_dir"
    log "NDK zip extract failed; will fallback to sdkmanager"
    return 1
  fi

  local extracted_ndk
  extracted_ndk=$(find "$extract_dir" -mindepth 1 -maxdepth 2 -type d -name "android-ndk-*" | head -n 1)
  if [[ -z "$extracted_ndk" || ! -d "$extracted_ndk" ]]; then
    rm -rf "$tmp_dir"
    log "Extracted NDK folder not found; will fallback to sdkmanager"
    return 1
  fi

  mkdir -p "$ANDROID_HOME/ndk"
  rm -rf "$ndk_dir"
  mv "$extracted_ndk" "$ndk_dir"
  rm -rf "$tmp_dir"

  if [[ -x "$ndk_dir/ndk-build" ]]; then
    log "Android NDK preinstall complete: $ANDROID_NDK_VERSION"
    return 0
  fi

  log "NDK preinstall verification failed; will fallback to sdkmanager"
  return 1
}

ensure_android_cmake() {
  if [[ -x "$ANDROID_HOME/cmake/$ANDROID_CMAKE_VERSION/bin/cmake" ]]; then
    log "Android CMake already present: $ANDROID_CMAKE_VERSION"
    return 0
  fi
  log "Installing Android CMake via sdkmanager: $ANDROID_CMAKE_VERSION"
  sdkmanager "cmake;$ANDROID_CMAKE_VERSION"
}

configure_arm64_cmake_emulation() {
  if ! is_arm64_host; then
    return 0
  fi

  local cmake_bin_dir="$ANDROID_HOME/cmake/$ANDROID_CMAKE_VERSION/bin"
  if [[ ! -d "$cmake_bin_dir" ]]; then
    log "Android CMake bin dir not found, skip ARM64 cmake emulation: $cmake_bin_dir"
    return 1
  fi

  install_packages box64 ninja-build file

  local wrapped_count=0
  local t
  for t in cmake ctest cpack; do
    local f="$cmake_bin_dir/$t"
    [[ -f "$f" ]] || continue

    if file "$f" | grep -q 'ELF 64-bit.*x86-64'; then
      local real_path="$cmake_bin_dir/.${t}.real"
      if [[ ! -f "$real_path" ]]; then
        mv "$f" "$real_path"
      fi
      cat > "$f" <<EOF
#!/usr/bin/env bash
exec box64 "$real_path" "\$@"
EOF
      chmod +x "$f"
      wrapped_count=$((wrapped_count + 1))
    fi
  done

  # Ninja 在 box64 下容易在 -t recompact/restat 崩，直接强制走本机 native ninja。
  if command_exists ninja; then
    cat > "$cmake_bin_dir/ninja" <<'EOF'
#!/usr/bin/env bash
exec /usr/bin/ninja "$@"
EOF
    chmod +x "$cmake_bin_dir/ninja"
  else
    log "native ninja not found in /usr/bin; keep original cmake ninja"
  fi

  # quick check
  if ! "$cmake_bin_dir/cmake" --version >/dev/null 2>&1; then
    log "ARM64 CMake emulation self-check failed"
    return 1
  fi

  log "Configured ARM64 CMake emulation in $cmake_bin_dir (wrapped $wrapped_count tools, ninja=native)"
}

configure_arm64_ndk_emulation() {
  if ! is_arm64_host; then
    return 0
  fi
  if [[ "$ENABLE_ARM64_NDK_EMULATION" != "1" ]]; then
    log "ARM64 NDK emulation disabled by ENABLE_ARM64_NDK_EMULATION=$ENABLE_ARM64_NDK_EMULATION"
    return 0
  fi

  local ndk_bin="$ANDROID_HOME/ndk/$ANDROID_NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64/bin"
  if [[ ! -d "$ndk_bin" ]]; then
    log "NDK linux-x86_64 toolchain dir not found, skip ARM64 emulation: $ndk_bin"
    return 1
  fi

  install_packages box64 file lld

  local wrapped_count=0
  local f
  for f in "$ndk_bin"/*; do
    [[ -f "$f" ]] || continue

    # preserve non-ELF launch scripts generated by NDK (keep shebang wrappers)
    if head -c 2 "$f" 2>/dev/null | grep -q '^#!'; then
      continue
    fi

    if file "$f" | grep -q 'ELF 64-bit.*x86-64'; then
      local base
      base=$(basename "$f")
      local real_path="$ndk_bin/.${base}.real"
      if [[ ! -f "$real_path" ]]; then
        mv "$f" "$real_path"
      fi
      cat > "$f" <<EOF
#!/usr/bin/env bash
exec box64 "$real_path" "\$@"
EOF
      chmod +x "$f"
      wrapped_count=$((wrapped_count + 1))
    fi
  done

  # Use native lld to avoid argv0 semantic mismatch under emulation.
  if [[ -x "/usr/bin/ld.lld" ]]; then
    cat > "$ndk_bin/lld" <<'EOF'
#!/usr/bin/env bash
exec /usr/bin/ld.lld "$@"
EOF
    chmod +x "$ndk_bin/lld"
    rm -f "$ndk_bin/ld.lld"
    ln -s lld "$ndk_bin/ld.lld"
  fi

  # quick compile+link self-check
  local test_src="/tmp/operit_ndk_test.c"
  local test_bin="/tmp/operit_ndk_test"
  echo 'int main(){return 0;}' > "$test_src"
  if ! "$ndk_bin/clang" --target=aarch64-none-linux-android24 --sysroot="$ANDROID_HOME/ndk/$ANDROID_NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64/sysroot" "$test_src" -o "$test_bin" >/dev/null 2>&1; then
    log "ARM64 NDK emulation self-check failed (clang link test)"
    return 1
  fi

  log "Configured ARM64 NDK emulation wrappers in $ndk_bin (wrapped $wrapped_count x86_64 tools)"
}

ensure_android_tools() {
  ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android}}"
  export ANDROID_HOME
  export ANDROID_SDK_ROOT="$ANDROID_HOME"

  if [[ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]]; then
    log "Downloading Android command line tools"
    install_packages unzip
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    local tmp_dir
    tmp_dir=$(mktemp -d)
    local zip_path="$tmp_dir/cmdline-tools.zip"
    local cmdline_url
    cmdline_url=$(select_download_url \
      "Android command line tools" \
      "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" \
      "dl.google.com" \
      "mirrors.tuna.tsinghua.edu.cn" "https://mirrors.tuna.tsinghua.edu.cn/android/repository/commandlinetools-linux-11076708_latest.zip" \
      "mirrors.bfsu.edu.cn" "https://mirrors.bfsu.edu.cn/android/repository/commandlinetools-linux-11076708_latest.zip" \
      "mirrors.aliyun.com" "https://mirrors.aliyun.com/android/repository/commandlinetools-linux-11076708_latest.zip")
    download_file "$cmdline_url" "$zip_path"
    unzip -q "$zip_path" -d "$ANDROID_HOME/cmdline-tools"
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
    rm -rf "$tmp_dir"
  fi

  export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
  log "Installing Android SDK packages"
  yes | sdkmanager --licenses >/dev/null || true
  sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
  ensure_android_cmake
  if ! configure_arm64_cmake_emulation; then
    log "ARM64 CMake emulation setup failed or skipped; CMake-based native builds may fail on ARM64 host"
  fi

  if ! ensure_android_ndk_preinstall; then
    log "Falling back to sdkmanager for NDK: $ANDROID_NDK_VERSION"
    sdkmanager "ndk;$ANDROID_NDK_VERSION"
  fi

  if ! configure_arm64_ndk_emulation; then
    log "ARM64 NDK emulation setup failed or skipped; native builds may still fail on ARM64 host"
  fi
}

ensure_gradle() {
  install_packages unzip
  mkdir -p "$GRADLE_ROOT"
  if command_exists gradle; then
    local installed_version
    installed_version=$(gradle --version 2>/dev/null | grep -oP 'Gradle \K[0-9.]+' | head -1 || true)
    if [[ -n "$installed_version" ]]; then
      log "System Gradle detected: $installed_version (still preparing local Gradle zip for wrapper cache)"
    else
      log "System Gradle detected (version parse failed); preparing local Gradle zip for wrapper cache"
    fi
  fi

  if [[ ! -f "$GRADLE_ZIP" ]]; then
    log "Downloading Gradle ${GRADLE_VERSION}"
    local gradle_url
    gradle_url=$(select_download_url \
      "Gradle distribution" \
      "https://services.gradle.org/distributions/${GRADLE_DIST}-bin.zip" \
      "services.gradle.org" \
      "mirrors.cloud.tencent.com" "https://mirrors.cloud.tencent.com/gradle/${GRADLE_DIST}-bin.zip" \
      "mirrors.aliyun.com" "https://mirrors.aliyun.com/gradle/${GRADLE_DIST}-bin.zip")
    download_file "$gradle_url" "$GRADLE_ZIP"
  else
    log "Gradle zip already present: $GRADLE_ZIP"
  fi

  if [[ ! -d "$GRADLE_ROOT/$GRADLE_DIST" ]]; then
    log "Extracting Gradle ${GRADLE_VERSION}"
    unzip -q "$GRADLE_ZIP" -d "$GRADLE_ROOT"
  else
    log "Local Gradle already extracted: $GRADLE_ROOT/$GRADLE_DIST"
  fi

  export GRADLE_HOME="$GRADLE_ROOT/$GRADLE_DIST"
  export PATH="$GRADLE_HOME/bin:$PATH"
}

update_gradle_wrapper_properties() {
  local wrapper_file="gradle/wrapper/gradle-wrapper.properties"
  if [[ ! -f "$wrapper_file" ]]; then
    return
  fi
  if [[ ! -f "$GRADLE_ZIP" ]]; then
    log "Gradle zip not found; keeping existing wrapper distributionUrl"
    return
  fi

  local gradle_zip_abs="$GRADLE_ZIP"
  if command_exists readlink; then
    gradle_zip_abs=$(readlink -f "$GRADLE_ZIP" 2>/dev/null || echo "$GRADLE_ZIP")
  fi
  local file_url="file\\://$gradle_zip_abs"

  if grep -q '^distributionUrl=' "$wrapper_file"; then
    sed -i "s|^distributionUrl=.*|distributionUrl=$file_url|" "$wrapper_file"
  else
    echo "distributionUrl=$file_url" >> "$wrapper_file"
  fi
  log "Wrapper distributionUrl set to local file: $gradle_zip_abs"
}

warmup_gradle_wrapper_cache() {
  if [[ ! -x "./gradlew" ]]; then
    log "gradlew not found; skipping wrapper cache warm-up"
    return 0
  fi
  if [[ ! -f "gradle/wrapper/gradle-wrapper.properties" ]]; then
    log "gradle-wrapper.properties not found; skipping wrapper cache warm-up"
    return 0
  fi
  log "Warming Gradle wrapper cache"
  if ! ./gradlew --version --no-daemon >/dev/null; then
    log "Wrapper cache warm-up failed; continuing"
    return 1
  fi
}

restore_gradle_properties() {
  cat > gradle.properties <<'EOF'
org.gradle.jvmargs=-Xmx8G -XX:MaxMetaspaceSize=4G -XX:ReservedCodeCacheSize=512m -XX:+HeapDumpOnOutOfMemoryError
org.gradle.workers.max=1
android.useAndroidX=true

# Proot/Termux Compatibility Settings
# Disable AAPT2 daemon mode to prevent "Daemon startup failed" errors in proot environment
android.aapt2.process.daemon=false
android.enableResourceOptimizations=false
EOF
}

restore_gradlew_bat() {
  cat > gradlew.bat <<'EOF'
@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar


@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if  not "" == "%GRADLE_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
EOF
}

update_local_properties() {
  local sdk_dir="$ANDROID_HOME"
  cat > local.properties <<EOF
## This file is automatically generated by Android Studio.
# Do not modify this file -- YOUR CHANGES WILL BE ERASED!
#
# This file should *NOT* be checked into Version Control Systems,
# as it contains information specific to your local configuration.
#
# Location of the SDK. This is only used by Gradle.
# For customization when using a Version Control System, please read the
# header note.
flutter.sdk=$FLUTTER_SDK
sdk.dir=$sdk_dir
EOF
}

configure_env_persistence() {
  local bashrc="$HOME/.bashrc"
  touch "$bashrc"
  local tmp_file
  tmp_file=$(mktemp)
  awk '
    /^# >>> operit flutter android env >>>$/ { skip = 1; next }
    /^# <<< operit flutter android env <<<$/ { skip = 0; next }
    skip != 1 { print }
  ' "$bashrc" > "$tmp_file"
  cat >> "$tmp_file" <<EOF
# >>> operit flutter android env >>>
export FLUTTER_ROOT=$FLUTTER_SDK
export FLUTTER_STORAGE_BASE_URL=$FLUTTER_STORAGE_BASE_URL
export PUB_HOSTED_URL=$PUB_HOSTED_URL
export JAVA_HOME=$JAVA_HOME
export ANDROID_HOME=$ANDROID_HOME
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=\$FLUTTER_ROOT/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$JAVA_HOME/bin:\$PATH
export GRADLE_USER_HOME=$GRADLE_USER_HOME
export GRADLE_HOME=${GRADLE_HOME:-$HOME/gradle/gradle-8.14}
export PATH=\$GRADLE_HOME/bin:\$PATH
export ENABLE_ARM64_NDK_EMULATION=$ENABLE_ARM64_NDK_EMULATION
# <<< operit flutter android env <<<
EOF
  mv "$tmp_file" "$bashrc"
  log "Environment variables updated in ~/.bashrc"
}

ensure_pub_get() {
  local project_root
  project_root=$(cd "$SCRIPT_DIR/.." && pwd)

  if [[ ! -f "$project_root/pubspec.yaml" ]]; then
    fail "pubspec.yaml not found in project root: $project_root"
  fi

  log "Running flutter pub get in project root: $project_root"
  if ! (cd "$project_root" && flutter pub get); then
    fail "flutter pub get failed in project root: $project_root"
  fi

  if [[ ! -f "$project_root/.dart_tool/package_config.json" ]]; then
    fail "package_config.json still missing after pub get: $project_root/.dart_tool/package_config.json"
  fi
}

warmup_gradle_cache_for_aapt2() {
  local gradle_cmd="$GRADLE_HOME/bin/gradle"
  if [[ ! -x "$gradle_cmd" ]]; then
    log "Local Gradle not found: $gradle_cmd"
    return 1
  fi
  log "Running warm-up Gradle task to resolve and execute AAPT2"
  if ! "$gradle_cmd" --no-daemon --rerun-tasks :app:processDebugResources; then
    log "AAPT2 pre-replace warm-up failed; continuing to patch aapt2"
    return 1
  fi
}

warmup_gradle_cache_after_aapt2_replace() {
  local gradle_cmd="$GRADLE_HOME/bin/gradle"
  if [[ ! -x "$gradle_cmd" ]]; then
    log "Local Gradle not found: $gradle_cmd"
    return 1
  fi
  log "Running post-replace warm-up to ensure patched AAPT2 is used"
  if ! "$gradle_cmd" --no-daemon --rerun-tasks :app:processDebugResources; then
    log "AAPT2 post-replace warm-up failed; setup will still continue"
    return 1
  fi
}

replace_aapt2() {
  local bundled_aapt2="$SCRIPT_DIR/tools/aapt2/aapt2-arm64-v8a"
  local expected_sha256="e5b5ff7f0d4f6ecd7fa5d05d77fed3f09f6f1bf80f078b8aada82bc578848561"
  if [[ ! -f "$bundled_aapt2" ]]; then
    fail "Bundled ARM64 aapt2 not found: $bundled_aapt2"
  fi

  local actual_sha256
  actual_sha256=$(sha256sum "$bundled_aapt2" | awk '{print $1}')
  if [[ "$actual_sha256" != "$expected_sha256" ]]; then
    fail "Bundled ARM64 aapt2 checksum mismatch: expected $expected_sha256, got $actual_sha256"
  fi

  local tmp_dir
  tmp_dir=$(mktemp -d)
  local aapt2_path="$tmp_dir/aapt2"
  log "Using bundled ARM64 aapt2 from template"
  cp "$bundled_aapt2" "$aapt2_path"
  chmod +x "$aapt2_path"

  local updated_sdk_count=0
  if [[ -d "$ANDROID_HOME/build-tools" ]]; then
    while IFS= read -r -d '' build_tools_dir; do
      if [[ -f "$build_tools_dir/aapt2" ]]; then
        cp "$aapt2_path" "$build_tools_dir/aapt2"
        updated_sdk_count=$((updated_sdk_count + 1))
      fi
    done < <(find "$ANDROID_HOME/build-tools" -mindepth 1 -maxdepth 1 -type d -print0)
  fi
  if [[ "$updated_sdk_count" -gt 0 ]]; then
    log "Replaced SDK build-tools aapt2 binaries: $updated_sdk_count"
  else
    log "No SDK build-tools aapt2 binaries found under: $ANDROID_HOME/build-tools"
  fi

  local gradle_cache_root="$GRADLE_USER_HOME/caches"
  local gradle_aapt_dir="$gradle_cache_root/modules-2/files-2.1/com.android.tools.build/aapt2"
  if [[ -d "$gradle_aapt_dir" ]]; then
    local updated_jar_count=0
    while IFS= read -r -d '' jar_path; do
      local jar_dir
      jar_dir=$(dirname "$jar_path")
      cp "$aapt2_path" "$jar_dir/aapt2"
      (cd "$jar_dir" && zip -q -f "$(basename "$jar_path")" aapt2)
      updated_jar_count=$((updated_jar_count + 1))
    done < <(find "$gradle_aapt_dir" -name "aapt2-*-linux.jar" -print0)
    log "Updated Gradle cache aapt2 jars: $updated_jar_count"
  else
    log "Gradle aapt2 module cache not found: $gradle_aapt_dir"
  fi

  local updated_transform_count=0
  while IFS= read -r -d '' transformed_aapt2; do
    cp "$aapt2_path" "$transformed_aapt2"
    updated_transform_count=$((updated_transform_count + 1))
  done < <(find "$gradle_cache_root" -type f -name "aapt2" -path "*/transforms*/*" -print0 2>/dev/null || true)

  if [[ "$updated_transform_count" -gt 0 ]]; then
    log "Updated transformed aapt2 binaries (recursive): $updated_transform_count"
  else
    log "No transformed aapt2 binaries found under: $gradle_cache_root"
  fi

  rm -rf "$tmp_dir"
}

main() {
  SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
  cd "$SCRIPT_DIR"

  if [[ -f "./gradlew" ]]; then
    chmod +x "./gradlew"
  fi

  install_packages wget curl aria2 unzip zip xz-utils git
  ensure_ping
  ensure_java
  resolve_java_home
  configure_flutter_storage_env
  configure_pub_hosted_url
  resolve_flutter_sdk
  ensure_flutter_arm64_dart_sdk
  configure_env_persistence
  bootstrap_flutter_sdk
  precache_flutter_sdk
  ensure_android_tools
  ensure_gradle
  update_gradle_wrapper_properties
  if ! warmup_gradle_wrapper_cache; then
    log "Ignoring wrapper warm-up error and continuing"
  fi
  restore_gradle_properties
  restore_gradlew_bat
  update_local_properties
  ensure_pub_get
  if ! warmup_gradle_cache_for_aapt2; then
    log "Ignoring pre-replace warm-up error and continuing to patch aapt2"
  fi
  replace_aapt2
  if ! warmup_gradle_cache_after_aapt2_replace; then
    log "Ignoring post-replace warm-up error and continuing"
  fi
  verify_setup_health

  log "Flutter Android environment setup complete"
  log "Reload shell or run: source ~/.bashrc"
}

main "$@"
