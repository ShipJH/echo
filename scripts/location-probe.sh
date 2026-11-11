#!/usr/bin/env bash
# Pleos 에뮬레이터에서 FusedLocation 이 실제로 위치를 주는지, 지오펜스 ENTER 가 오는지 잰다.
# 테스트가 기다리는 동안 호스트에서 `adb emu geo fix` 로 서울시청 → 해운대 순으로 위치를 넣는다.
# 사용: ANDROID_SERIAL=emulator-5554 bash scripts/location-probe.sh
set -euo pipefail
cd "$(dirname "$0")/.."
SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
export ANDROID_SERIAL="${ANDROID_SERIAL:-emulator-5554}"

bash gradlew -q :app:installDebug :app:installDebugAndroidTest
# AAOS 는 포그라운드 사용자가 10 번이라 --user 없이 주면 사용자 0 에만 부여된다.
"$ADB" shell pm grant --user current com.eco pleos.car.permission.FUSED_LOCATION
"$ADB" logcat -c

inject() {
  # geo fix 는 경도, 위도 순서다.
  for _ in $(seq 1 8); do "$ADB" emu geo fix 126.9780 37.5665 >/dev/null; sleep 2; done   # 서울시청
  for _ in $(seq 1 20); do "$ADB" emu geo fix 129.1604 35.1587 >/dev/null; sleep 2; done  # 해운대
}
inject &
INJECTOR=$!
trap 'kill $INJECTOR 2>/dev/null || true' EXIT

"$ADB" shell am instrument -w -e class com.eco.FusedLocationProbeTest \
  com.eco.test/androidx.test.runner.AndroidJUnitRunner || true
echo "--- eco-location-probe"
"$ADB" logcat -d -s eco-location-probe
