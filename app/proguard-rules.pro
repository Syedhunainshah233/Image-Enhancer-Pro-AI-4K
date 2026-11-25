# Image Enhancer Pro - R8 rules for release builds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ONNX Runtime calls back into its Java classes from native code.
-keep class ai.onnxruntime.** { *; }
