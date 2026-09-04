# Regole ProGuard per la build release (attualmente minify disabilitato).
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable <methods>;
}
