# Preserve generic signatures for Gson TypeToken<List<SList>>
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep Gson TypeToken and subclasses
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { <init>(); }

# Keep persisted data models & enum values for VaultManager JSON serialization
-keep class com.tailgunnerx.listgen.SList { *; }
-keep class com.tailgunnerx.listgen.ListItem { *; }
-keepclassmembers enum com.tailgunnerx.listgen.ItemStatus { *; }
