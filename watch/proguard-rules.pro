# 课表 App 的 R8 规则（watch 模块）

# ---------- Gson 反射序列化 ----------
# shared 模块的 Timetable/Course 等数据类靠 Gson 反射读写，
# 字段名被混淆后 JSON 键就对不上了，必须整体 keep。
-keep class com.classwatch.shared.** { *; }
-keepattributes *Annotation*, Signature

# ---------- Compose ----------
# Compose 自带 consumer rules，一般不用额外配置。
# 稳妥起见保留 kotlin 协程与元数据相关属性。
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-dontwarn kotlinx.coroutines.**
