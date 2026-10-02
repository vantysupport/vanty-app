# kotlinx.serialization: conservar los serializadores generados de nuestros modelos
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class xyz.vanty.aba.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class xyz.vanty.aba.**$$serializer { *; }

# Ktor / supabase-kt
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
