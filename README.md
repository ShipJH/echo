# ECHO (에코)

차량 안에서 음성을 녹음해 날짜 또는 장소 조건에 따라 다시 들을 수 있는 타임캡슐 앱입니다.

## 실행 방법
[apk 위치](https://github.com/ShipJH/echo/tree/main/apk)

1. `apk/도전하는청년들_echo_release.apk`를 Pleos Connect 환경에 설치합니다.
2. 앱을 실행하고 화면의 음성 입력 또는 녹음 기능을 사용합니다.
3. 음성 입력과 녹음을 처음 사용할 때 마이크 권한을 허용합니다.

## 테스트 환경 및 필요한 설정

- Pleos Connect Emulator v2.1.0
- Android API 34, x86_64
- 화면: 2560×1440 가로, 160 dpi (AVD 설정 기준)
- 음성 입력 및 녹음을 위해 마이크 권한이 필요합니다.
- 장소 기반 열기 기능을 사용하려면 Pleos 위치 권한과 에뮬레이터 위치 설정이 필요합니다.
- Android SDK의 Pleos system image: `android-34;pleos_car_emulator;x86_64` (revision 1)

## APK

- APK 파일 위치 또는 GitHub 링크: [apk 폴더](https://github.com/ShipJH/echo/tree/main/apk)
