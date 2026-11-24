# Reglas de R8 para Mani.
# kotlinx.serialization: conservar los serializers generados de los DTOs @Serializable del catálogo.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class ar.param.advisor.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ar.param.advisor.**
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ar.param.advisor.** {
    kotlinx.serialization.KSerializer serializer(...);
}
