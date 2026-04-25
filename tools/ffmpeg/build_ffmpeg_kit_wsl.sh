#!/usr/bin/env bash
set -eo pipefail

FFMPEG_KIT_DIR="${1:-$HOME/build/ffmpeg-kit}"
PROXY_HOST="${OPERIT_PROXY_HOST:-172.23.176.1}"
PROXY_PORT="${OPERIT_PROXY_PORT:-7890}"

prepare_sources() {
  export BASEDIR="${FFMPEG_KIT_DIR}"
  export FFMPEG_KIT_BUILD_TYPE="android"

  # shellcheck source=/dev/null
  source "${BASEDIR}/scripts/variable.sh"
  # shellcheck source=/dev/null
  source "${BASEDIR}/scripts/function-${FFMPEG_KIT_BUILD_TYPE}.sh"

  enable_default_android_architectures
  enable_default_android_libraries
  enable_main_build
  optimize_for_speed

  disable_arch arm-v7a
  disable_arch arm-v7a-neon
  disable_arch x86
  disable_arch x86-64

  enable_library android-zlib

  local enabled_libraries=(
    fontconfig
    freetype
    fribidi
    gmp
    gnutls
    lame
    libass
    libiconv
    libtheora
    libvorbis
    libvpx
    libwebp
    libxml2
    opencore-amr
    shine
    speex
    dav1d
    kvazaar
    libilbc
    opus
    snappy
    soxr
    twolame
    vo-amrwbenc
    zimg
  )

  local library
  for library in "${enabled_libraries[@]}"; do
    enable_library "${library}"
  done

  echo "Preparing sources"
  download_gnu_config
  downloaded_library_sources "${ENABLED_LIBRARIES[@]}"
}

if [[ ! -d "$FFMPEG_KIT_DIR" ]]; then
  echo "ffmpeg-kit repo not found: $FFMPEG_KIT_DIR" >&2
  exit 1
fi

export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT_OVERRIDE:-/mnt/d/ProgramData/AndroidSDK}"
export ANDROID_HOME="${ANDROID_HOME_OVERRIDE:-${ANDROID_SDK_ROOT}}"
export ANDROID_NDK_ROOT="${ANDROID_NDK_ROOT_OVERRIDE:-$HOME/android-sdk/ndk/22.1.7171670}"
if [[ -n "${JAVA_HOME_OVERRIDE:-}" ]]; then
  export JAVA_HOME="${JAVA_HOME_OVERRIDE}"
elif [[ -d "$HOME/.local/jbr-17" ]]; then
  export JAVA_HOME="$HOME/.local/jbr-17"
elif [[ -d "$HOME/.local/jbrsdk-17.0.12-linux-x64-b1207.37" ]]; then
  export JAVA_HOME="$HOME/.local/jbrsdk-17.0.12-linux-x64-b1207.37"
else
  export JAVA_HOME="/usr/lib/jvm/java-21-openjdk"
fi
export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${JAVA_HOME}/bin"

export http_proxy="http://${PROXY_HOST}:${PROXY_PORT}"
export https_proxy="${http_proxy}"
export HTTP_PROXY="${http_proxy}"
export HTTPS_PROXY="${http_proxy}"
export GRADLE_OPTS="${GRADLE_OPTS:-} -Dhttp.proxyHost=${PROXY_HOST} -Dhttp.proxyPort=${PROXY_PORT} -Dhttps.proxyHost=${PROXY_HOST} -Dhttps.proxyPort=${PROXY_PORT}"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhttp.proxyHost=${PROXY_HOST} -Dhttp.proxyPort=${PROXY_PORT} -Dhttps.proxyHost=${PROXY_HOST} -Dhttps.proxyPort=${PROXY_PORT}"
unset ALL_PROXY
unset all_proxy
export NO_PROXY="localhost,127.0.0.1,::1"
export no_proxy="${NO_PROXY}"

cd "$FFMPEG_KIT_DIR"

git config --global http.version HTTP/1.1 >/dev/null 2>&1 || true

if [[ ! -x "${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ]]; then
  echo "Linux NDK not found or invalid at ${ANDROID_NDK_ROOT}" >&2
  exit 1
fi

mkdir -p "${FFMPEG_KIT_DIR}/android"
cat > "${FFMPEG_KIT_DIR}/android/local.properties" <<EOF
sdk.dir=${ANDROID_SDK_ROOT}
ndk.dir=${ANDROID_NDK_ROOT}
EOF

prepare_sources

# Some bundled build scripts pin automake 1.16 binary names even on newer distros.
TOOLS_COMPAT_DIR="${FFMPEG_KIT_DIR}/.tools-compat"
mkdir -p "${TOOLS_COMPAT_DIR}"
ln -sf "$(command -v aclocal)" "${TOOLS_COMPAT_DIR}/aclocal-1.16"
ln -sf "$(command -v automake)" "${TOOLS_COMPAT_DIR}/automake-1.16"
export PATH="${TOOLS_COMPAT_DIR}:${PATH}"

# ffmpeg-kit's libiconv bootstrap misses two m4 files for libcharset on newer distros.
if [[ -d "${FFMPEG_KIT_DIR}/src/libiconv" ]]; then
  if [[ -f "${FFMPEG_KIT_DIR}/src/libiconv/autogen.sh" ]]; then
    sed -i 's/for file in codeset.m4 fcntl-o.m4 lib-ld.m4 relocatable.m4 relocatable-lib.m4 visibility.m4;/for file in codeset.m4 fcntl-o.m4 lib-ld.m4 build-to-host.m4 host-cpu-c-abi.m4 relocatable.m4 relocatable-lib.m4 visibility.m4;/g' "${FFMPEG_KIT_DIR}/src/libiconv/autogen.sh"
  fi
  if [[ -f "${FFMPEG_KIT_DIR}/src/libiconv/srcm4/build-to-host.m4" ]]; then
    cp -f "${FFMPEG_KIT_DIR}/src/libiconv/srcm4/build-to-host.m4" "${FFMPEG_KIT_DIR}/src/libiconv/libcharset/m4/"
  fi
  if [[ -f "${FFMPEG_KIT_DIR}/src/libiconv/srcm4/host-cpu-c-abi.m4" ]]; then
    cp -f "${FFMPEG_KIT_DIR}/src/libiconv/srcm4/host-cpu-c-abi.m4" "${FFMPEG_KIT_DIR}/src/libiconv/libcharset/m4/"
  fi
  if [[ -f "${FFMPEG_KIT_DIR}/src/libiconv/Makefile.devel" ]]; then
    sed -i 's/aclocal-1\.16/aclocal/g' "${FFMPEG_KIT_DIR}/src/libiconv/Makefile.devel"
    sed -i 's/automake-1\.16/automake/g' "${FFMPEG_KIT_DIR}/src/libiconv/Makefile.devel"
  fi
  if [[ -f "${FFMPEG_KIT_DIR}/src/libiconv/libcharset/Makefile.devel" ]]; then
    sed -i 's/aclocal-1\.16/aclocal/g' "${FFMPEG_KIT_DIR}/src/libiconv/libcharset/Makefile.devel"
  fi
fi

export RECONF_libiconv="${RECONF_libiconv:-1}"
export RECONF_gnutls="${RECONF_gnutls:-1}"

# cpu-features tries to download googletest during configure; disable tests for offline/reproducible builds.
if [[ -f "${FFMPEG_KIT_DIR}/scripts/android/cpu-features.sh" ]]; then
  if ! grep -q -- '-DBUILD_TESTING=OFF' "${FFMPEG_KIT_DIR}/scripts/android/cpu-features.sh"; then
    sed -i 's~$(android_ndk_cmake) || return 1~$(android_ndk_cmake) -DBUILD_TESTING=OFF || return 1~' "${FFMPEG_KIT_DIR}/scripts/android/cpu-features.sh"
  fi
fi

# gnutls bootstrap drags optional openssl submodule checks into the Android build path.
if [[ -d "${FFMPEG_KIT_DIR}/src/gnutls" ]]; then
  if [[ -f "${FFMPEG_KIT_DIR}/src/gnutls/bootstrap.conf" ]]; then
    perl -0pi -e 's/ devel\/openssl//g' "${FFMPEG_KIT_DIR}/src/gnutls/bootstrap.conf"
  fi
  if [[ -f "${FFMPEG_KIT_DIR}/src/gnutls/bootstrap" ]]; then
    sed -i "/git submodule | grep '\\^-'/c\\if \$use_git && git submodule | grep '^-' | grep -v ' devel/openssl\$' >/dev/null; then" "${FFMPEG_KIT_DIR}/src/gnutls/bootstrap"
    sed -i "/^ >\\/dev\\/null; then$/,+3d" "${FFMPEG_KIT_DIR}/src/gnutls/bootstrap"
  fi

  git -C "${FFMPEG_KIT_DIR}/src/gnutls" submodule sync --recursive || true
  git -C "${FFMPEG_KIT_DIR}/src/gnutls" submodule update --init --depth 1 \
    gnulib \
    devel/libtasn1 \
    devel/openssl \
    devel/nettle \
    devel/abi-dump \
    cligen \
    tests/suite/tls-fuzzer/python-ecdsa \
    tests/suite/tls-fuzzer/tlsfuzzer \
    tests/suite/tls-fuzzer/tlslite-ng \
    tests/suite/tls-interoperability || true

  if [[ -d "${FFMPEG_KIT_DIR}/src/gnutls/devel/libtasn1" ]]; then
    git -C "${FFMPEG_KIT_DIR}/src/gnutls/devel/libtasn1" restore --source=HEAD --worktree --staged . || true
  fi
fi

# Match the currently vendored ffmpeg-kit feature set as closely as possible.
exec ./android.sh \
  -f \
  -s \
  --api-level=24 \
  --disable-arm-v7a \
  --disable-arm-v7a-neon \
  --disable-x86 \
  --disable-x86-64 \
  --enable-fontconfig \
  --enable-freetype \
  --enable-fribidi \
  --enable-gmp \
  --enable-gnutls \
  --enable-lame \
  --enable-libass \
  --enable-libiconv \
  --enable-libtheora \
  --enable-libvorbis \
  --enable-libvpx \
  --enable-libwebp \
  --enable-libxml2 \
  --enable-opencore-amr \
  --enable-shine \
  --enable-speex \
  --enable-dav1d \
  --enable-kvazaar \
  --enable-libilbc \
  --enable-opus \
  --enable-snappy \
  --enable-soxr \
  --enable-twolame \
  --enable-vo-amrwbenc \
  --enable-zimg
