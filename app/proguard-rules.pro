# Add project specific ProGuard rules here.

# La persistencia (SharedPreferences + JSON) serializa enums por name() y los lee
# con valueOf(): hay que conservar los nombres de las constantes para no romper
# datos ya guardados (WorkMode, WeightType, StepKind, Phase, DisplayMode, ConfirmMode...).
-keepclassmembers enum com.minitimer.** { *; }
