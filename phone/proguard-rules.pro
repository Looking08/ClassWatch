# 课表 App 的 R8 规则（phone 模块）

# ---------- Gson 反射序列化 ----------
-keep class com.classwatch.shared.** { *; }
-keepattributes *Annotation*, Signature

# ---------- Compose ----------
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-dontwarn kotlinx.coroutines.**
