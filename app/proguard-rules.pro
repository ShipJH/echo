# Pleos SpeechToText 3.3.1 AAR 에는 consumer ProGuard 규칙이 없다(FusedLocation·Vehicle 은 있다, 2026-11-11 확인).
# STT 는 AIDL 로 차량 서비스와 통신하고 콜백 인터페이스를 서비스가 부르므로 이름과 멤버를 그대로 둔다.
-keep class ai.pleos.playground.stt.** { *; }
-keep interface ai.pleos.playground.stt.** { *; }
-keep class ai.pleos.playground.caas.** { *; }
-keep interface ai.pleos.playground.caas.** { *; }
-keep class ai.pleos.playground.sdk.stt.** { *; }
