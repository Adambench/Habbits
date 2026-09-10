# Room and kotlinx.serialization ship their own R8 rules as consumer rules, so
# the entries here cover only what this app adds.

# Serializable classes are found reflectively through their generated
# serializers, so the companion objects R8 would otherwise strip must stay.
-keepclassmembers class io.github.adambench.habbits.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.adambench.habbits.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# The sealed sync event hierarchy is deserialised by its @SerialName, not by
# class reference, so nothing in the code points at the subclasses.
-keep class io.github.adambench.habbits.sync.SyncEvent { *; }
-keep class * implements io.github.adambench.habbits.sync.SyncEvent { *; }

# Room's generated database constructor is referenced only by generated code.
-keep class io.github.adambench.habbits.data.** { *; }

-dontwarn org.jetbrains.annotations.**
