# MapLibre usa JNI: sus clases no se pueden ofuscar ni eliminar
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
# Tink (security-crypto) referencia anotaciones que no viajan en el APK
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
