#!/usr/bin/env bash
# لوسفېر مىنىس — بارلىق رەسمىي ئورۇنلاشتۇرما بولاقلارنى قۇرۇش.
#
# بۇ قەدەم ئەسلى ئامبارنىڭ BUILDING.md دىكى Android باسقۇچلىرىنى
# ئاپتوماتلاشتۇرىدۇ ۋە ھەر بىر بولاقنى ``dist/`` غا، ئەپ ئىچىدىكى
# يېڭىلاش تەكشۈرۈشى ئوقۇيدىغان نام فورماتىدا چىقىرىدۇ:
#   LOSFER-Minis-<نەشر>-build<نومۇر>-arm64-v8a.apk        (github يولى)
#   LOSFER-Minis-<نەشر>-build<نومۇر>-arm64-v8a-store.apk  (دۇكان يولى APK)
#   LOSFER-Minis-<نەشر>-build<نومۇر>.aab                 (دۇكان يولى AAB)
#   LOSFER-Minis-<نەشر>-build<نومۇر>-native-debug-symbols.zip
# ھەممىسى رەسمىي Release غا ئايرىم بولاق سۈپىتىدە يوللىنىدۇ.
#
# مۇھىت ئۆزگەرگۈچىلىرى: JAVA_HOME, ANDROID_HOME, ANDROID_NDK_HOME,
# GOROOT, GOPATH, GOPROXY (ھەممىسى ئىختىيارىي، بايقالمىسا ئۆزلۈكىدىن تېپىلىدۇ).

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export JAVA_HOME="${JAVA_HOME:-/opt/jdk17}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/28.0.12433566}"
export GOROOT="${GOROOT:-/opt/go}"
export GOPATH="${GOPATH:-/opt/gopath}"
export GOPROXY="${GOPROXY:-https://goproxy.cn,https://proxy.golang.org,direct}"
export PATH="$JAVA_HOME/bin:$GOROOT/bin:$GOPATH/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

VERSION="$(python3 -c 'import re,pathlib;t=pathlib.Path("src/android/app/build.gradle.kts").read_text();print(re.search(r"versionName\s*=\s*\"([^\"]+)\"",t).group(1))')"
CODE="$(python3 -c 'import re,pathlib;t=pathlib.Path("src/android/app/build.gradle.kts").read_text();print(re.search(r"versionCode\s*=\s*(\d+)",t).group(1))')"
echo "=== LOSFER Minis $VERSION (build $CODE) قۇرۇلۇۋاتىدۇ ==="

echo "--- [1/5] ئەسلى كود قوشۇمچە ئامبارلىرى"
git submodule update --init --recursive deps/proot

echo "--- [2/5] rclone AAR"
# ئامباردا ساقلانغان AAR نى ئىشلىتىمىز: ئۇ ئوخشاش Go مەنبەسىدىن
# قۇرۇلغان، شۇڭا ھەر بىر ئاپتوماتلاشقان قۇرۇشنى Go/gomobile/گىرەلمە
# توپلامىغا موھتاج قىلمايدۇ. كەم بولسىلا مەنبەدىن قايتا قۇرۇلىدۇ.
mkdir -p src/android/app/libs
if [ -f src/android/app/libs/rclone.aar ]; then
  echo "تەييار rclone.aar ئىشلىتىلىدۇ"
elif command -v gomobile >/dev/null 2>&1; then
  ./deps/build_rclone_android.sh
  cp -f deps/build/rclone/rclone.aar src/android/app/libs/rclone.aar
else
  echo "خاتالىق: rclone.aar يوق، gomobile مۇ يوق" >&2
  exit 1
fi

echo "--- [3/5] يەرلىك (native) باغلىنىشلار"
./deps/build_proot.sh
./scripts/prepare_android_sandbox.sh

echo "--- [4/5] Gradle: ھەر بىر يولنىڭ APK ۋە AAB بولاقلىرى"
cd src/android
# ئاساسلىق بولاق: GitHub Releases غا چىقىدىغان، ئۆزىنى يېڭىلاشنى تەكشۈرىدىغان APK.
./gradlew :app:assembleRelease --no-daemon --stacktrace
# دۇكان يولىنىڭ APK ۋە AAB بولاقلىرى. بۇلار مەغلۇپ بولسىمۇ ئاساسلىق
# APK نى يوققا چىقارمايمىز؛ قايسى بولاق قۇرۇلغان بولسا شۇ يوللىنىدۇ.
./gradlew :app:assembleStoreRelease --no-daemon --stacktrace || echo "ئاگاھلاندۇرۇش: store APK قۇرۇلمىدى" >&2
./gradlew :app:bundleStoreRelease --no-daemon --stacktrace || echo "ئاگاھلاندۇرۇش: AAB قۇرۇلمىدى" >&2
cd "$ROOT"

echo "--- [5/5] بارلىق بولاقلارنى يىغىش"
DIST="$ROOT/dist"
rm -rf "$DIST" && mkdir -p "$DIST"

APK_NAME="LOSFER-Minis-${VERSION}-build${CODE}-arm64-v8a.apk"
STORE_APK_NAME="LOSFER-Minis-${VERSION}-build${CODE}-arm64-v8a-store.apk"
AAB_NAME="LOSFER-Minis-${VERSION}-build${CODE}.aab"
SYMBOLS_NAME="LOSFER-Minis-${VERSION}-build${CODE}-native-debug-symbols.zip"

# ئاساسلىق github APK: outputs/apk/release/app-release.apk ياكى
# outputs/apk/github/release/app-github-release.apk بولىدۇ.
APK_SRC="$(find src/android/app/build/outputs/apk/release src/android/app/build/outputs/apk/github/release \
  -type f -name '*.apk' ! -name '*unsigned*' 2>/dev/null | head -1 || true)"
# store APK نى ئايرىم ئىزدەيمىز، ئاساسلىق APK بىلەن ئارىلاشمىسۇن.
STORE_APK_SRC="$(find src/android/app/build/outputs/apk/store -type f -name '*release*.apk' \
  ! -name '*unsigned*' 2>/dev/null | head -1 || true)"
AAB_SRC="$(find src/android/app/build/outputs/bundle -type f -name '*.aab' 2>/dev/null | head -1 || true)"
SYMBOLS_SRC="$(find src/android/app/build/outputs/native-debug-symbols -type f -name '*.zip' 2>/dev/null | head -1 || true)"

if [ -z "$APK_SRC" ]; then
  echo "خاتالىق: ئاساسلىق release APK تېپىلمىدى" >&2
  exit 1
fi
cp -f "$APK_SRC" "$DIST/$APK_NAME"
if [ -n "$STORE_APK_SRC" ]; then
  cp -f "$STORE_APK_SRC" "$DIST/$STORE_APK_NAME"
else
  echo "ئاگاھلاندۇرۇش: store APK تېپىلمىدى" >&2
fi
if [ -n "$AAB_SRC" ]; then
  cp -f "$AAB_SRC" "$DIST/$AAB_NAME"
else
  echo "ئاگاھلاندۇرۇش: AAB تېپىلمىدى" >&2
fi
if [ -n "$SYMBOLS_SRC" ]; then
  cp -f "$SYMBOLS_SRC" "$DIST/$SYMBOLS_NAME"
fi

# SHA256SUMS ئۆزىنى ئۆز ئىچىگە ئالمىسۇن.
(cd "$DIST" && find . -maxdepth 1 -type f ! -name SHA256SUMS -printf '%f\n' \
  | sort | xargs sha256sum > SHA256SUMS)
echo "=== تەييار بولاقلار ==="
ls -lh "$DIST"
cat "$DIST/SHA256SUMS"
echo "=== قۇرۇش تامام ==="
