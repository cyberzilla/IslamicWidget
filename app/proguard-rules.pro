# Mengamankan engine IslamicAstronomy kustom
-keep class com.cyberzilla.islamicwidget.utils.IslamicAstronomy { *; }
-keep class com.cyberzilla.islamicwidget.utils.IslamicAstronomy$* { *; }

# Mengamankan library Astronomy (cosinekitty) untuk kalkulasi lunar Hijriyah
-keep class io.github.cosinekitty.astronomy.** { *; }
-keepclassmembers class io.github.cosinekitty.astronomy.** { *; }