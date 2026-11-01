# Aero 코드는 이름을 바꾸지 않음 (앱 화면에서 부르는 기능 보호)
-keep class com.dlsdml99.aero.** { *; }
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,JavascriptInterface
# 글자·바코드 인식 연결 부분은 그대로 둠
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_** { *; }
-ignorewarnings
