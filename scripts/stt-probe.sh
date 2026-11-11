#!/usr/bin/env bash
# Pleos 에뮬레이터에서 STT 받아쓰기(sendAudio)가 되는지, 얼마나 걸리는지 잰다.
# macOS `say` 로 한국어 문장을 만들어 앱 녹음과 같은 형식(48 kHz PCM16 mono, 44바이트 헤더 WAV)으로 바꾼 뒤
# androidTest assets 에 넣고 SttProbeTest 를 돌린다. 생성한 음성은 저장소에 넣지 않는다(.gitignore).
# 사용: ANDROID_SERIAL=emulator-5554 bash scripts/stt-probe.sh
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"
OUT=app/src/androidTest/assets/stt
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$OUT"

make() { # 이름 문장
  say -v Yuna -o "$TMP/$1.aiff" "$2"
  afconvert -f WAVE -d LEI16@48000 -c 1 "$TMP/$1.aiff" "$TMP/$1.raw.wav"
  # afconvert 는 FLLR 같은 추가 청크를 넣는다. 앱 녹음처럼 44바이트 헤더로 다시 쓴다.
  python3 -I - "$TMP/$1.raw.wav" "$OUT/$1.wav" <<'PY'
import sys, wave
with wave.open(sys.argv[1], 'rb') as src:
    params, frames = src.getparams(), src.readframes(src.getnframes())
with wave.open(sys.argv[2], 'wb') as dst:
    dst.setparams(params); dst.writeframes(frames)
PY
}
make cmd_year "일 년 뒤에 열어 줘"
make cmd_place "여기 다시 오면 들려 줘"
make cmd_keep "그냥 보관해 줘"
make cmd_record "녹음해 줘"
make memory "우리 지우 첫 바다 가는 날이야. 지우야, 지금 기분이 어때? 두근두근해."

bash gradlew -q :app:installDebug :app:installDebugAndroidTest
"$ADB" logcat -c
"$ADB" shell am instrument -w -e class com.eco.SttProbeTest com.eco.test/androidx.test.runner.AndroidJUnitRunner || true
echo "--- eco-stt-probe"
"$ADB" logcat -d -s eco-stt-probe eco-stt
