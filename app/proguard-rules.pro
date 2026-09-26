# O código do próprio app fica inteiro (workers, receivers, widget e billing são chamados pelo sistema).
-keep class br.com.celularsaudavel.** { *; }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
