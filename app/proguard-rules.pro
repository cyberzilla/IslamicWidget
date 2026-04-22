# Mengamankan library Adhan2 KMP agar tidak dirusak oleh Minify
-keep class com.batoulapps.adhan2.** { *; }
-keepclassmembers class com.batoulapps.adhan2.** { *; }

# Mengamankan library Astronomy (cosinekitty) untuk kalkulasi lunar Hijriyah
-keep class io.github.cosinekitty.astronomy.** { *; }
-keepclassmembers class io.github.cosinekitty.astronomy.** { *; }