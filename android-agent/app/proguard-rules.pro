# DTOs sérialisés par Gson (réflexion sur les noms de champs)
-keep class com.tms.agent.net.Dtos { *; }
-keep class com.tms.agent.net.Dtos$* { *; }

# Retrofit
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod
-keep,allowobfuscation interface com.tms.agent.net.TmsApi
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
