# 崩溃栈可读：保留源文件与行号，配合 build/outputs/mapping/release/mapping.txt 反混淆
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room 实体/DAO 由 KSP 编译期生成直接构造代码，理论上不需要 keep。
# 但这台机器没有设备可以验证 release 包的运行时行为，所以保留数据层类名：
# 代价是几十 KB 未被混淆，换来的是不会因反射边缘路径在真机上崩。
-keep class com.klocktx.moneyblocks.data.local.** { *; }
-keep class com.klocktx.moneyblocks.domain.usecase.ExportRow { *; }

# reorderable 用 remember(list, spacing) 以「列表实例」作为状态 key，
# 并对 Animatable 列表做索引访问。R8 的对象合并/内联理论上不该动这些语义，
# 但这个包没有真机验证过 release，而它一崩就是整页打不开——按同样理由保下来。
-keep class sh.calvin.reorderable.** { *; }

# Room、DataStore、Compose 各自随 AAR 附带 consumer rules，这里不重复声明

# 刻意不写 `-assumenosideeffects android.util.Log`：它会连库内部的错误日志一起静默，
# 而这个包还没有真机验证过，保留日志比省几 KB 重要。
