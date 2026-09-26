# DTOs sérialisés par Gson (réflexion sur les noms de champs)
-keep class com.tms.agent.net.Dtos { *; }
-keep class com.tms.agent.net.Dtos$* { *; }
# Données persistées en JSON (file de statuts) : noms de champs stables d'une version à l'autre
-keep class com.tms.agent.core.StatusOutbox$Item { *; }

# Gson : R8 en mode complet retire la signature générique des sous-classes anonymes de TypeToken
# ("Missing type parameter"). Règles officielles Gson.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Retrofit
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation interface com.tms.agent.net.TmsApi
# R8 en mode complet (défaut depuis AGP 8) retire les signatures génériques de Call/Response :
# Retrofit ne peut alors plus lire Call<T> ("Unable to create call adapter"). Règles officielles Retrofit.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# MESDK Newland (JNI + réflexion interne) : ne pas renommer
-keep class com.newland.** { *; }
-keep class android.newland.** { *; }
-dontwarn com.newland.**
-dontwarn android.newland.**

# NeptuneLite PAX (liaison au service système par réflexion / AIDL)
-keep class com.pax.** { *; }
-dontwarn com.pax.**

# PayLib Sunmi (stubs AIDL du service système)
-keep class com.sunmi.** { *; }
-keep class sunmi.paylib.** { *; }
-dontwarn com.sunmi.**

# Interface AIDL exposée aux applications de paiement
-keep class com.tms.agent.ITmsAgentService { *; }
-keep class com.tms.agent.ITmsAgentService$* { *; }
