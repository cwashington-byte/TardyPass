# kotlinx.serialization: keep serializers for our models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.iar.tardypass.** {
    *** Companion;
}
-keepclasseswithmembers class com.iar.tardypass.**$$serializer { *; }
