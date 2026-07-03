package com.minitimer.model

/** Modo de salida de audio cuando hay audífonos conectados. */
const val HEADSET_ONLY = 0
const val SPEAKER_AND_HEADSET = 1

/** Modo de tema de la app. */
const val THEME_AUTO = 0
const val THEME_LIGHT = 1
const val THEME_DARK = 2

/**
 * Configuración de alarma/sonido. Es un bloque reutilizable e INDEPENDIENTE por
 * mini-app (cada pestaña tiene su propia instancia): sonido, volumen, vibración,
 * y comportamiento frente a silencio/audífonos.
 */
data class AlarmConfig(
    /** URI del tono elegido; null = sonido de alarma por defecto del sistema. */
    val soundUri: String? = null,
    /** Nombre legible del tono elegido, para mostrarlo en ajustes. */
    val soundName: String? = null,
    /** Volumen de la alarma, 0f..1f (curva perceptual en dB al reproducir). */
    val volume: Float = 0.25f,
    /** Vibrar al sonar. */
    val vibrationEnabled: Boolean = false,
    /** Índice del patrón de vibración en [VIBRATION_PATTERNS]. */
    val vibrationPattern: Int = 0,
    /** Sonar aunque el equipo esté en silencio / No molestar. */
    val ignoreSilent: Boolean = true,
    /** Salida de audio con audífonos: [HEADSET_ONLY] o [SPEAKER_AND_HEADSET]. */
    val headsetMode: Int = HEADSET_ONLY,
)

/** Ajustes generales, comunes a TODA la app (no dependen de la pestaña). */
data class GeneralConfig(
    val accent: Long = 0xFFFF5252,
    val language: String = "en",
    /** Tema: [THEME_AUTO] (sigue el sistema), [THEME_LIGHT] o [THEME_DARK]. */
    val themeMode: Int = THEME_AUTO,
    /** Modo desarrollador: muestra el número de cada pantalla. */
    val developerMode: Boolean = false,
)

/** Ajustes específicos de la pestaña Timer. */
data class TimerConfig(
    val presets: List<Int> = listOf(60, 180, 300, 600, 900, 1800),
    val autoDismiss: Int = 3,
    /** Incremento (en segundos) del botón "+tiempo" de cada tarjeta. */
    val addIncrementSec: Int = 30,
    /** Mostrar el anillo de progreso sobre la cámara (overlay). */
    val showRing: Boolean = false,
    /** Mostrar la cápsula/tarjeta flotante (overlay). */
    val showOverlay: Boolean = false,
    /** Promover la notificación como chip / Now Bar. */
    val showNowBar: Boolean = true,
    /** Alarma propia de la pestaña Timer. */
    val alarm: AlarmConfig = AlarmConfig(),
)

/** Ajustes específicos de la pestaña Athlete. */
data class AthleteConfig(
    /** Reloj del player con ceros a la izquierda: "00:30" en vez de "30". */
    val padPlayerClock: Boolean = false,
    /** Alarma propia de la pestaña Athlete. */
    val alarm: AlarmConfig = AlarmConfig(),
)

/** Ajustes específicos de la pestaña Water (placeholder; se ampliará). */
data class WaterConfig(
    /** Alarma propia de la pestaña Water. */
    val alarm: AlarmConfig = AlarmConfig(),
)

/** Rango del tamaño de texto del OSD del reloj, en pt. */
const val OSD_TEXT_SIZE_MIN = 10
const val OSD_TEXT_SIZE_MAX = 40
const val OSD_TEXT_SIZE_DEFAULT = 14
const val OSD_TEXT_SIZE_STEP = 2

/**
 * Un panel del OSD (reloj flotante): franja fina e INDEPENDIENTE que se pinta
 * sobre otras apps. Contenido a la izquierda (hora), indicadores a la derecha
 * (volumen [n], batería [n%]). Cada panel tiene su propio contenido, apariencia
 * y posición.
 */
data class OsdPanel(
    /** Panel activo (se pinta el overlay). */
    val enabled: Boolean = false,
    // --- Contenido ---
    val showTime: Boolean = true,
    /** Segundos en vivo en la hora ("3:14:05"). */
    val showSeconds: Boolean = true,
    /** Nivel de volumen multimedia, ej. "[5]". */
    val showVolume: Boolean = true,
    /** Porcentaje de batería, ej. "[62%]". */
    val showBattery: Boolean = true,
    /** Mostrar estado de carga cuando está enchufado, ej. "[AC]"/"[USB]". */
    val showCharging: Boolean = false,
    /** Formato 24 horas (si no, 12h). */
    val use24h: Boolean = false,
    val showDate: Boolean = false,
    val showMemo: Boolean = false,
    val memo: String = "",
    // --- Apariencia ---
    /** Tamaño del texto en pt (ver [OSD_TEXT_SIZE_MIN]/[OSD_TEXT_SIZE_MAX]). */
    val textSizeSp: Int = OSD_TEXT_SIZE_DEFAULT,
    val textColor: Long = 0xFFFFFFFF,
    /** Cambia el color del texto automáticamente según el modo oscuro/claro. */
    val autoDarkColor: Boolean = false,
    /** Opacidad del fondo 0f..1f (0 = fondo transparente, sin caja). */
    val bgAlpha: Float = 0f,
    // Nota: la posición (x,y) del overlay se guarda aparte en SettingsStore
    // (como el offset del anillo) para que el arrastre desde el servicio no
    // choque con la copia en memoria de la config del ViewModel.
)

/** Ajustes de la mini-app Reloj: OSD flotante con 2 paneles independientes. */
data class ClockConfig(
    val panel1: OsdPanel = OsdPanel(),
    val panel2: OsdPanel = OsdPanel(),
) {
    fun panel(index: Int): OsdPanel = if (index == 0) panel1 else panel2
    fun withPanel(index: Int, p: OsdPanel): ClockConfig =
        if (index == 0) copy(panel1 = p) else copy(panel2 = p)

    /** ¿Algún panel activo? Determina si el servicio de overlay debe correr. */
    val anyEnabled: Boolean get() = panel1.enabled || panel2.enabled
}

/** Paleta de color de texto para el OSD del reloj. */
val OSD_TEXT_COLORS: List<Long> = listOf(
    0xFFFFFFFF,
    0xFF4AC0D6,
    0xFF3DDC84,
    0xFFA06CFF,
    0xFFFF5252,
    0xFFFFD54A,
)

/**
 * Configuración completa de la app: un bloque general + uno por cada mini-app
 * (pestaña). Añadir una pestaña nueva = añadir su data class aquí.
 */
data class AppConfig(
    val general: GeneralConfig = GeneralConfig(),
    val timer: TimerConfig = TimerConfig(),
    val athlete: AthleteConfig = AthleteConfig(),
    val clock: ClockConfig = ClockConfig(),
    val water: WaterConfig = WaterConfig(),
)

/** Opciones (en segundos) para el incremento del botón "+tiempo". */
val ADD_INCREMENT_OPTIONS: List<Int> = listOf(15, 30, 60, 300)

/** Patrón de vibración con nombre y tiempos (ms) para waveform en bucle. */
data class VibrationPattern(val name: String, val timings: LongArray)

/**
 * Patrones de vibración disponibles. Cada arreglo son tiempos en ms que se
 * repiten en bucle: el primer valor es la espera inicial, luego alternan
 * vibración/silencio.
 */
val VIBRATION_PATTERNS: List<VibrationPattern> = listOf(
    VibrationPattern("Simple", longArrayOf(0, 500, 300)),
    VibrationPattern("Zig-Zig", longArrayOf(0, 200, 120, 200, 500)),
    VibrationPattern("Zig-zig-zig", longArrayOf(0, 180, 100, 180, 100, 180, 500)),
    VibrationPattern("Tap", longArrayOf(0, 70, 350)),
    VibrationPattern("Knock", longArrayOf(0, 60, 90, 60, 600)),
    VibrationPattern("Heartbeat", longArrayOf(0, 130, 110, 260, 600)),
    VibrationPattern("Bounce", longArrayOf(0, 320, 220, 220, 150, 140, 90, 100, 60, 500)),
    VibrationPattern("Dubstep", longArrayOf(0, 220, 90, 90, 90, 380, 110, 200, 350)),
    VibrationPattern("Gallop", longArrayOf(0, 80, 90, 80, 260, 500)),
)

/** Paleta de colores de acento disponibles. */
val ACCENT_COLORS: List<Long> = listOf(
    0xFF4AC0D6,
    0xFF4A90D6,
    0xFF3DDC84,
    0xFFA06CFF,
    0xFF9E9E9E,
    0xFFFF69B4,
    0xFFFF5252,
)

/** Opciones de auto-descarte en segundos (0 = desactivado). */
val AUTO_DISMISS_OPTIONS: List<Int> = listOf(0, 3, 5, 10, 30, 60)
