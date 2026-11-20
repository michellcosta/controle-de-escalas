# Regras R8 — reescritas na auditoria (o arquivo antigo mantinha TUDO do
# Firebase/Compose com -keep classe inteira, anulando o R8: dex de 18MB).

# ==== Modelos Firestore ====
# toObject do Firestore usa reflexao sobre os campos dos data classes —
# mantem os campos (os nomes vao pro Firestore).
-keep class com.controleescalas.app.data.models.** { *; }

# ==== kotlinx.serialization (SessionManager/SessionData/MotoristaQRCode) ====
# O plugin kotlinx.serialization ja embute consumer rules; estas sao as
# canonicas por garantia:
-keep,includedescriptorclasses class com.controleescalas.app.**$$serializer { *; }
-keepclassmembers class com.controleescalas.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.controleescalas.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ==== Crash logs legiveis ====
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
