import React, { useEffect, useRef, useState } from 'react';
import {
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  Vibration,
  View,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import Svg, { Circle, G } from 'react-native-svg';
import {
  useFonts,
  JetBrainsMono_300Light,
  JetBrainsMono_400Regular,
  JetBrainsMono_600SemiBold,
} from '@expo-google-fonts/jetbrains-mono';

const ACCENT = '#4ac0d6';
const TRACK = '#2a2d2f';
const BG = '#000000';

// JetBrains Mono via @expo-google-fonts (bundled: web + nativo). Su 0 se distingue de la O.
const NUM_FONT_WEB = "'JetBrains Mono', 'Consolas', 'SF Mono', 'Roboto Mono', monospace";
const FONT_LIGHT = 'JetBrainsMono_300Light';
const FONT_SEMIBOLD = 'JetBrainsMono_600SemiBold';

const RING_SIZE = 300;
const STROKE = 14;
const R = (RING_SIZE - STROKE) / 2;
const CX = RING_SIZE / 2;
const CY = RING_SIZE / 2;
const CIRC = 2 * Math.PI * R;

// Presets por defecto (en segundos).
const DEFAULT_PRESETS = [60, 180, 300, 600, 900, 1800];

// Paleta de colores de acento.
const COLORS = ['#4ac0d6', '#4a90d6', '#3ddc84', '#a06cff', '#ff9f43', '#ff5c8a', '#ff5252'];

const I18N = {
  es: {
    locale: 'es-ES',
    title: 'Temporizador',
    start: 'Iniciar',
    cancel: 'Cancelar',
    pause: 'Pausa',
    resume: 'Reanudar',
    dismiss: 'Descartar',
    restart: 'Reiniciar',
    timeUp: '¡Tiempo!',
    paused: 'En pausa',
    endsAt: 'Termina a las',
    settings: 'Configuración',
    language: 'Idioma',
    color: 'Color',
    presets: 'Presets',
    add: 'Agregar',
    reset: 'Restablecer valores',
    presetPlaceholder: 'mm:ss o hh:mm:ss',
    miniWindow: 'Ventana flotante',
    pipUnsupported: 'Tu navegador no soporta la ventana flotante. Usa Chrome o Edge.',
    autoDismiss: 'Auto descartar',
    floatingWindow: 'Ventana flotante',
    on: 'Activado',
    off: 'Desactivado',
  },
  en: {
    locale: 'en-US',
    title: 'Timer',
    start: 'Start',
    cancel: 'Cancel',
    pause: 'Pause',
    resume: 'Resume',
    dismiss: 'Dismiss',
    restart: 'Restart',
    timeUp: "Time's up!",
    paused: 'Paused',
    endsAt: 'Ends at',
    settings: 'Settings',
    language: 'Language',
    color: 'Color',
    presets: 'Presets',
    add: 'Add',
    reset: 'Reset to defaults',
    presetPlaceholder: 'mm:ss or hh:mm:ss',
    miniWindow: 'Floating window',
    pipUnsupported: 'Your browser does not support the floating window. Use Chrome or Edge.',
    autoDismiss: 'Auto dismiss',
    floatingWindow: 'Floating window',
    on: 'On',
    off: 'Off',
  },
};

const STORE_KEY = 'mini-timer-settings';
const DEFAULTS = { accent: '#ff5252', language: 'en', presets: DEFAULT_PRESETS, autoDismiss: 3, floatingWindow: false };

function loadSettings() {
  try {
    if (Platform.OS === 'web' && typeof localStorage !== 'undefined') {
      const raw = localStorage.getItem(STORE_KEY);
      if (raw) return { ...DEFAULTS, ...JSON.parse(raw) };
    }
  } catch (e) {
    // ignore
  }
  return DEFAULTS;
}

function saveSettings(s) {
  try {
    if (Platform.OS === 'web' && typeof localStorage !== 'undefined') {
      localStorage.setItem(STORE_KEY, JSON.stringify(s));
    }
  } catch (e) {
    // ignore
  }
}

function parsePresetInput(str) {
  const clean = (str || '').trim();
  if (!clean) return 0;
  const parts = clean.split(':').map((p) => parseInt(p, 10));
  if (parts.some((n) => Number.isNaN(n))) return 0;
  let sec = 0;
  if (parts.length === 1) sec = parts[0] * 60; // un solo número = minutos
  else if (parts.length === 2) sec = parts[0] * 60 + parts[1];
  else if (parts.length === 3) sec = parts[0] * 3600 + parts[1] * 60 + parts[2];
  return sec > 0 ? sec : 0;
}

function dedupeSorted(arr) {
  return Array.from(new Set(arr)).sort((a, b) => a - b);
}

const KEYS = [
  ['1', '2', '3'],
  ['4', '5', '6'],
  ['7', '8', '9'],
  ['00', '0', '⌫'],
];

const pad2 = (n) => String(n).padStart(2, '0');

function formatRemaining(ms) {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  if (h > 0) return `${h}:${pad2(m)}:${pad2(s)}`;
  return `${m}:${pad2(s)}`;
}

function formatClock(date, locale) {
  return date.toLocaleTimeString(locale || [], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

// Alarma sonora en web mediante Web Audio API.
function startWebBeep() {
  const Ctx = window.AudioContext || window.webkitAudioContext;
  if (!Ctx) return () => {};
  const ctx = new Ctx();
  const beep = () => {
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.type = 'sine';
    osc.frequency.value = 880;
    const t = ctx.currentTime;
    gain.gain.setValueAtTime(0.0001, t);
    gain.gain.exponentialRampToValueAtTime(0.4, t + 0.02);
    gain.gain.exponentialRampToValueAtTime(0.0001, t + 0.4);
    osc.start(t);
    osc.stop(t + 0.42);
  };
  beep();
  const id = setInterval(beep, 800);
  return () => {
    clearInterval(id);
    ctx.close();
  };
}

export default function App() {
  // phase: 'setup' | 'running' | 'paused' | 'done'
  const [phase, setPhase] = useState('setup');
  const [digits, setDigits] = useState('');
  const [totalMs, setTotalMs] = useState(0);
  const [remainingMs, setRemainingMs] = useState(0);

  const [fontsLoaded] = useFonts({
    JetBrainsMono_300Light,
    JetBrainsMono_400Regular,
    JetBrainsMono_600SemiBold,
  });

  const [settings, setSettings] = useState(loadSettings);
  const [showSettings, setShowSettings] = useState(false);
  useEffect(() => { saveSettings(settings); }, [settings]);
  const accent = settings.accent;
  const t = I18N[settings.language] || I18N.es;
  const locale = t.locale;

  const endAtRef = useRef(0);
  const alarmStopRef = useRef(null);
  const pipWinRef = useRef(null);
  const pipElRef = useRef(null);

  // ----- Tiempo configurado a partir de los dígitos -----
  const padded = digits.padStart(6, '0');
  const setH = parseInt(padded.slice(0, 2), 10);
  const setM = parseInt(padded.slice(2, 4), 10);
  const setS = parseInt(padded.slice(4, 6), 10);
  const totalSeconds = setH * 3600 + setM * 60 + setS;

  // ----- Tick del countdown -----
  useEffect(() => {
    if (phase !== 'running') return undefined;
    const tick = () => {
      const left = endAtRef.current - Date.now();
      if (left <= 0) {
        setRemainingMs(0);
        setPhase('done');
        startAlarm();
      } else {
        setRemainingMs(left);
      }
    };
    tick();
    const id = setInterval(tick, 100);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [phase]);

  useEffect(() => stopAlarm, []);

  // Cierra la ventana flotante al desmontar.
  useEffect(() => () => {
    if (pipWinRef.current) {
      pipWinRef.current.close();
      pipWinRef.current = null;
    }
  }, []);

  // Sincroniza la ventana flotante (Picture-in-Picture) y el título de la pestaña.
  useEffect(() => {
    if (Platform.OS !== 'web') return;
    const done = phase === 'done';
    const label = done ? t.timeUp : formatRemaining(remainingMs);
    if (pipElRef.current) {
      pipElRef.current.textContent = label;
      pipElRef.current.style.color = done ? '#ff5252' : accent;
    }
    if (typeof document !== 'undefined') {
      document.title = phase === 'setup' ? t.title : `${label} \u00b7 ${t.title}`;
    }
    if (phase === 'setup' && pipWinRef.current) {
      pipWinRef.current.close();
      pipWinRef.current = null;
      pipElRef.current = null;
    }
  }, [remainingMs, phase, accent, t]);

  const openMiniWindow = async () => {
    if (Platform.OS !== 'web' || typeof window === 'undefined') return;
    if (!('documentPictureInPicture' in window)) {
      if (typeof window.alert === 'function') window.alert(t.pipUnsupported);
      return;
    }
    if (pipWinRef.current) {
      pipWinRef.current.focus();
      return;
    }
    try {
      const pip = await window.documentPictureInPicture.requestWindow({ width: 120, height: 64 });
      const doc = pip.document;
      doc.title = t.title;
      const fontLink = doc.createElement('link');
      fontLink.rel = 'stylesheet';
      fontLink.href = 'https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@300;400&display=swap';
      doc.head.appendChild(fontLink);
      Object.assign(doc.body.style, {
        margin: '0',
        height: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#000000',
        fontFamily: 'system-ui, -apple-system, sans-serif',
      });
      const el = doc.createElement('div');
      Object.assign(el.style, {
        fontSize: '28px',
        fontWeight: '300',
        fontFamily: NUM_FONT_WEB,
        fontVariantNumeric: 'tabular-nums slashed-zero',
        color: phase === 'done' ? '#ff5252' : accent,
      });
      el.textContent = phase === 'done' ? t.timeUp : formatRemaining(remainingMs);
      doc.body.appendChild(el);
      pipWinRef.current = pip;
      pipElRef.current = el;
      pip.addEventListener('pagehide', () => {
        pipWinRef.current = null;
        pipElRef.current = null;
      });
    } catch (e) {
      // ignore
    }
  };

  // Abre la ventana flotante al salir de foco la pestaña; la cierra al volver.
  useEffect(() => {
    if (Platform.OS !== 'web' || typeof document === 'undefined') return undefined;
    const onVisibility = () => {
      const active = phase === 'running' || phase === 'paused' || phase === 'done';
      if (document.hidden && active && settings.floatingWindow) {
        openMiniWindow();
      } else if (!document.hidden && pipWinRef.current) {
        pipWinRef.current.close();
        pipWinRef.current = null;
        pipElRef.current = null;
      }
    };
    document.addEventListener('visibilitychange', onVisibility);
    return () => document.removeEventListener('visibilitychange', onVisibility);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [phase, settings.floatingWindow]);

  // Auto-descarte cuando el timer termina.
  useEffect(() => {
    if (phase !== 'done') return undefined;
    const secs = settings.autoDismiss;
    if (!secs || secs <= 0) return undefined;
    const id = setTimeout(() => dismiss(), secs * 1000);
    return () => clearTimeout(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [phase, settings.autoDismiss]);

  // Favicon de timer (web), con el color de acento.
  useEffect(() => {
    if (Platform.OS !== 'web' || typeof document === 'undefined') return;
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64"><g fill="none" stroke="${accent}" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"><line x1="24" y1="6" x2="40" y2="6"/><line x1="32" y1="6" x2="32" y2="13"/><circle cx="32" cy="37" r="22"/><line x1="32" y1="37" x2="32" y2="23"/><line x1="32" y1="37" x2="42" y2="42"/></g></svg>`;
    const url = `data:image/svg+xml,${encodeURIComponent(svg)}`;
    let link = document.querySelector("link[rel~='icon']");
    if (!link) {
      link = document.createElement('link');
      link.rel = 'icon';
      document.head.appendChild(link);
    }
    link.type = 'image/svg+xml';
    link.href = url;
  }, [accent]);

  const startAlarm = () => {
    stopAlarm();
    if (Platform.OS === 'web') {
      alarmStopRef.current = startWebBeep();
    } else {
      Vibration.vibrate([0, 600, 400, 600, 400, 600, 400], true);
      alarmStopRef.current = () => Vibration.cancel();
    }
  };

  const stopAlarm = () => {
    if (alarmStopRef.current) {
      alarmStopRef.current();
      alarmStopRef.current = null;
    }
  };

  // ----- Teclado -----
  const onKey = (k) => {
    if (k === '⌫') {
      setDigits((p) => p.slice(0, -1));
      return;
    }
    setDigits((p) => {
      if (k === '00') {
        if (p.length === 0) return p;
        if (p.length >= 6) return p;
        return p.length >= 5 ? p + '0' : p + '00';
      }
      if (p.length >= 6) return p;
      if (p.length === 0 && k === '0') return p;
      return p + k;
    });
  };

  // ----- Acciones del timer -----
  const startWithSeconds = (sec) => {
    if (sec <= 0) return;
    const ms = sec * 1000;
    endAtRef.current = Date.now() + ms;
    setTotalMs(ms);
    setRemainingMs(ms);
    setPhase('running');
  };

  const start = () => startWithSeconds(totalSeconds);

  const pause = () => {
    setRemainingMs(Math.max(0, endAtRef.current - Date.now()));
    setPhase('paused');
  };

  const resume = () => {
    endAtRef.current = Date.now() + remainingMs;
    setPhase('running');
  };

  const cancel = () => {
    stopAlarm();
    setPhase('setup');
  };

  const restart = () => {
    stopAlarm();
    startWithSeconds(Math.round(totalMs / 1000));
  };

  const dismiss = () => {
    stopAlarm();
    setDigits('');
    setPhase('setup');
  };

  if (!fontsLoaded) {
    return (
      <View style={styles.container}>
        <StatusBar style="light" />
      </View>
    );
  }

  // ===================== RENDER: SETTINGS =====================
  if (showSettings) {
    return (
      <SettingsScreen
        settings={settings}
        setSettings={setSettings}
        onClose={() => setShowSettings(false)}
        t={t}
      />
    );
  }

  // ===================== RENDER: SETUP =====================
  if (phase === 'setup') {
    const canStart = totalSeconds > 0;
    return (
      <View style={styles.container}>
        <StatusBar style="light" />
        <View style={styles.topBar}>
          <View style={styles.gear} />
          <Text style={[styles.header, { marginBottom: 0 }]}>{t.title}</Text>
          <Pressable style={styles.gear} onPress={() => setShowSettings(true)}>
            <Text style={styles.gearIcon}>⚙</Text>
          </Pressable>
        </View>

        <View style={styles.setupTime}>
          <TimePart value={pad2(setH)} unit="h" active={setH > 0} accent={accent} />
          <TimePart value={pad2(setM)} unit="m" active={setM > 0 || setH > 0} accent={accent} />
          <TimePart value={pad2(setS)} unit="s" active accent={accent} />
        </View>

        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          style={styles.presets}
          contentContainerStyle={styles.presetsContent}
        >
          {settings.presets.map((sec) => (
            <Pressable
              key={sec}
              style={styles.presetChip}
              onPress={() => startWithSeconds(sec)}
            >
              <Text style={[styles.presetText, { color: accent }]}>{formatRemaining(sec * 1000)}</Text>
            </Pressable>
          ))}
        </ScrollView>

        <View style={styles.keypad}>
          {KEYS.map((row, i) => (
            <View key={i} style={styles.keyRow}>
              {row.map((k) => (
                <Pressable
                  key={k}
                  style={styles.key}
                  android_ripple={{ color: '#333', borderless: true }}
                  onPress={() => onKey(k)}
                >
                  <Text style={styles.keyText}>{k}</Text>
                </Pressable>
              ))}
            </View>
          ))}
        </View>

        <Pressable
          style={[styles.startBtn, { backgroundColor: accent }, !canStart && styles.startBtnDisabled]}
          disabled={!canStart}
          onPress={start}
        >
          <Text style={styles.startBtnText}>{t.start}</Text>
        </Pressable>
      </View>
    );
  }

  // ===================== RENDER: RUNNING / PAUSED / DONE =====================
  const progress = totalMs > 0 ? Math.max(0, remainingMs / totalMs) : 0;
  const dashoffset = CIRC * (1 - progress);
  const isDone = phase === 'done';
  const endDate =
    phase === 'running'
      ? new Date(endAtRef.current)
      : new Date(Date.now() + remainingMs);

  return (
    <View style={styles.container}>
      <StatusBar style="light" />
      <Text style={styles.header}>{t.title}</Text>

      <View style={styles.ringWrap}>
        <Svg width={RING_SIZE} height={RING_SIZE}>
          <Circle
            cx={CX}
            cy={CY}
            r={R}
            stroke={TRACK}
            strokeWidth={STROKE}
            fill="none"
          />
          <G rotation={-90} origin={`${CX}, ${CY}`}>
            <Circle
              cx={CX}
              cy={CY}
              r={R}
              stroke={isDone ? '#ff5252' : accent}
              strokeWidth={STROKE}
              strokeLinecap="round"
              fill="none"
              strokeDasharray={CIRC}
              strokeDashoffset={isDone ? CIRC : dashoffset}
            />
          </G>
        </Svg>

        <View style={styles.ringCenter}>
          {isDone ? (
            <Text style={styles.doneText}>{t.timeUp}</Text>
          ) : (
            <>
              <Text style={styles.remaining}>{formatRemaining(remainingMs)}</Text>
              <Text style={styles.endsAt}>
                {phase === 'paused' ? t.paused : `${t.endsAt} ${formatClock(endDate, locale)}`}
              </Text>
            </>
          )}
        </View>
      </View>

      <View style={styles.controls}>
        {isDone ? (
          <>
            <ControlButton label={t.dismiss} onPress={dismiss} variant="muted" accent={accent} />
            <ControlButton label={t.restart} onPress={restart} variant="accent" accent={accent} />
          </>
        ) : (
          <>
            <ControlButton label={t.cancel} onPress={cancel} variant="muted" accent={accent} />
            {phase === 'running' ? (
              <ControlButton label={t.pause} onPress={pause} variant="accent" accent={accent} />
            ) : (
              <ControlButton label={t.resume} onPress={resume} variant="accent" accent={accent} />
            )}
          </>
        )}
      </View>

      {Platform.OS === 'web' && settings.floatingWindow && (
        <Pressable style={styles.miniBtn} onPress={openMiniWindow}>
          <Text style={styles.miniBtnText}>{t.miniWindow}</Text>
        </Pressable>
      )}
    </View>
  );
}

function TimePart({ value, unit, active, accent }) {
  return (
    <View style={styles.timePart}>
      <Text style={[styles.timeValue, !active && styles.timeValueDim]}>{value}</Text>
      <Text style={[styles.timeUnit, { color: accent }, !active && styles.timeValueDim]}>{unit}</Text>
    </View>
  );
}

function ControlButton({ label, onPress, variant, accent }) {
  return (
    <Pressable
      onPress={onPress}
      style={[
        styles.control,
        variant === 'accent' ? [styles.controlAccent, { backgroundColor: accent }] : styles.controlMuted,
      ]}
    >
      <Text
        style={[
          styles.controlText,
          variant === 'accent' && styles.controlTextAccent,
        ]}
      >
        {label}
      </Text>
    </Pressable>
  );
}

function SettingsScreen({ settings, setSettings, onClose, t }) {
  const [presetInput, setPresetInput] = useState('');
  const accent = settings.accent;

  const addPreset = () => {
    const sec = parsePresetInput(presetInput);
    if (!sec) return;
    setSettings((s) => ({ ...s, presets: dedupeSorted([...s.presets, sec]) }));
    setPresetInput('');
  };
  const removePreset = (sec) => {
    setSettings((s) => ({ ...s, presets: s.presets.filter((x) => x !== sec) }));
  };

  return (
    <View style={styles.container}>
      <StatusBar style="light" />
      <View style={styles.topBar}>
        <Pressable style={styles.gear} onPress={onClose}>
          <Text style={styles.gearIcon}>←</Text>
        </Pressable>
        <Text style={[styles.header, { marginBottom: 0 }]}>{t.settings}</Text>
        <View style={styles.gear} />
      </View>

      <ScrollView
        style={styles.settingsScroll}
        contentContainerStyle={styles.settingsContent}
        showsVerticalScrollIndicator={false}
      >
        <Text style={styles.settingLabel}>{t.language}</Text>
        <View style={styles.row}>
          {[['es', 'Español'], ['en', 'English']].map(([code, name]) => (
            <Pressable
              key={code}
              style={[styles.optionChip, settings.language === code && { backgroundColor: accent }]}
              onPress={() => setSettings((s) => ({ ...s, language: code }))}
            >
              <Text style={[styles.optionText, settings.language === code && styles.optionTextActive]}>{name}</Text>
            </Pressable>
          ))}
        </View>

        <Text style={styles.settingLabel}>{t.color}</Text>
        <View style={styles.swatchRow}>
          {COLORS.map((c) => (
            <Pressable
              key={c}
              style={[styles.swatch, { backgroundColor: c }, settings.accent === c && styles.swatchActive]}
              onPress={() => setSettings((s) => ({ ...s, accent: c }))}
            />
          ))}
        </View>

        <Text style={styles.settingLabel}>{t.presets}</Text>
        <View style={styles.presetEditWrap}>
          {settings.presets.map((sec) => (
            <View key={sec} style={styles.presetEditChip}>
              <Text style={[styles.presetText, { color: accent }]}>{formatRemaining(sec * 1000)}</Text>
              <Pressable onPress={() => removePreset(sec)} style={styles.removeBtn}>
                <Text style={styles.removeBtnText}>×</Text>
              </Pressable>
            </View>
          ))}
        </View>
        <View style={styles.addRow}>
          <TextInput
            style={styles.input}
            value={presetInput}
            onChangeText={setPresetInput}
            placeholder={t.presetPlaceholder}
            placeholderTextColor="#5a5d5f"
            onSubmitEditing={addPreset}
          />
          <Pressable style={[styles.addBtn, { backgroundColor: accent }]} onPress={addPreset}>
            <Text style={styles.addBtnText}>{t.add}</Text>
          </Pressable>
        </View>

        {Platform.OS === 'web' && (
          <>
            <Text style={styles.settingLabel}>{t.floatingWindow}</Text>
            <View style={styles.row}>
              {[[true, t.on], [false, t.off]].map(([val, name]) => (
                <Pressable
                  key={String(val)}
                  style={[styles.optionChip, settings.floatingWindow === val && { backgroundColor: accent }]}
                  onPress={() => setSettings((s) => ({ ...s, floatingWindow: val }))}
                >
                  <Text style={[styles.optionText, settings.floatingWindow === val && styles.optionTextActive]}>{name}</Text>
                </Pressable>
              ))}
            </View>
          </>
        )}

        <Text style={styles.settingLabel}>{t.autoDismiss}</Text>
        <View style={styles.row}>
          {[0, 3, 5, 10, 30, 60].map((v) => (
            <Pressable
              key={v}
              style={[styles.optionChip, settings.autoDismiss === v && { backgroundColor: accent }]}
              onPress={() => setSettings((s) => ({ ...s, autoDismiss: v }))}
            >
              <Text style={[styles.optionText, settings.autoDismiss === v && styles.optionTextActive]}>{v === 0 ? t.off : `${v}s`}</Text>
            </Pressable>
          ))}
        </View>

        <Pressable style={styles.resetBtn} onPress={() => setSettings(DEFAULTS)}>
          <Text style={styles.resetBtnText}>{t.reset}</Text>
        </Pressable>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: BG,
    paddingTop: 64,
    paddingHorizontal: 24,
    alignItems: 'center',
  },
  header: {
    color: '#fff',
    fontSize: 22,
    fontWeight: '600',
    marginBottom: 24,
  },
  // ---- Setup ----
  setupTime: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'center',
    marginVertical: 24,
  },
  timePart: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    marginHorizontal: 6,
  },
  timeValue: {
    color: '#fff',
    fontSize: 56,
    fontWeight: '300',
    fontFamily: FONT_LIGHT,
    fontVariant: ['tabular-nums', 'slashed-zero'],
  },
  timeUnit: {
    color: ACCENT,
    fontSize: 22,
    fontWeight: '500',
    marginLeft: 2,
    marginBottom: 10,
  },
  timeValueDim: {
    color: '#5a5d5f',
  },
  presets: {
    maxHeight: 44,
    marginBottom: 12,
    flexGrow: 0,
  },
  presetsContent: {
    paddingHorizontal: 4,
    alignItems: 'center',
  },
  presetChip: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    backgroundColor: '#1c1e1f',
    borderRadius: 20,
    marginRight: 10,
  },
  presetText: {
    color: ACCENT,
    fontSize: 15,
    fontWeight: '600',
    fontFamily: FONT_SEMIBOLD,
    fontVariant: ['tabular-nums', 'slashed-zero'],
  },
  keypad: {
    width: '100%',
    maxWidth: 360,
    marginTop: 8,
  },
  keyRow: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginVertical: 6,
  },
  key: {
    width: 88,
    height: 72,
    alignItems: 'center',
    justifyContent: 'center',
  },
  keyText: {
    color: '#fff',
    fontSize: 30,
    fontWeight: '400',
  },
  startBtn: {
    marginTop: 20,
    backgroundColor: ACCENT,
    paddingHorizontal: 48,
    paddingVertical: 16,
    borderRadius: 32,
  },
  startBtnDisabled: {
    backgroundColor: '#2a2d2f',
  },
  startBtnText: {
    color: '#001316',
    fontSize: 18,
    fontWeight: '700',
  },
  // ---- Running / done ----
  ringWrap: {
    width: RING_SIZE,
    height: RING_SIZE,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 24,
  },
  ringCenter: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
  },
  remaining: {
    color: '#fff',
    fontSize: 56,
    fontWeight: '300',
    fontFamily: FONT_LIGHT,
    fontVariant: ['tabular-nums', 'slashed-zero'],
  },
  endsAt: {
    color: '#9aa0a3',
    fontSize: 15,
    marginTop: 8,
  },
  doneText: {
    color: '#ff5252',
    fontSize: 44,
    fontWeight: '600',
  },
  controls: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    width: '100%',
    maxWidth: 360,
    marginTop: 48,
  },
  control: {
    flex: 1,
    marginHorizontal: 8,
    paddingVertical: 16,
    borderRadius: 32,
    alignItems: 'center',
  },
  controlMuted: {
    backgroundColor: '#1c1e1f',
  },
  controlAccent: {
    backgroundColor: ACCENT,
  },
  controlText: {
    color: '#fff',
    fontSize: 17,
    fontWeight: '600',
  },
  controlTextAccent: {
    color: '#001316',
  },
  // ---- Top bar / settings ----
  topBar: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    width: '100%',
    maxWidth: 420,
    marginBottom: 24,
  },
  gear: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
  },
  gearIcon: {
    color: '#9aa0a3',
    fontSize: 24,
  },
  settingsScroll: {
    width: '100%',
    maxWidth: 420,
  },
  settingsContent: {
    paddingBottom: 48,
  },
  settingLabel: {
    color: '#fff',
    fontSize: 16,
    fontWeight: '600',
    marginTop: 24,
    marginBottom: 12,
  },
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  optionChip: {
    paddingHorizontal: 18,
    paddingVertical: 10,
    backgroundColor: '#1c1e1f',
    borderRadius: 20,
    marginRight: 10,
    marginBottom: 10,
  },
  optionText: {
    color: '#cfd3d6',
    fontSize: 15,
    fontWeight: '600',
  },
  optionTextActive: {
    color: '#001316',
  },
  swatchRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  swatch: {
    width: 40,
    height: 40,
    borderRadius: 20,
    marginRight: 12,
    marginBottom: 12,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  swatchActive: {
    borderColor: '#fff',
  },
  presetEditWrap: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  presetEditChip: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#1c1e1f',
    borderRadius: 20,
    paddingLeft: 16,
    paddingRight: 8,
    paddingVertical: 8,
    marginRight: 10,
    marginBottom: 10,
  },
  removeBtn: {
    marginLeft: 8,
    width: 22,
    height: 22,
    borderRadius: 11,
    backgroundColor: '#33363a',
    alignItems: 'center',
    justifyContent: 'center',
  },
  removeBtnText: {
    color: '#fff',
    fontSize: 16,
    lineHeight: 18,
  },
  addRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 4,
  },
  input: {
    flex: 1,
    backgroundColor: '#1c1e1f',
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    color: '#fff',
    fontSize: 16,
    marginRight: 10,
  },
  addBtn: {
    paddingHorizontal: 22,
    paddingVertical: 12,
    borderRadius: 12,
  },
  addBtnText: {
    color: '#001316',
    fontSize: 16,
    fontWeight: '700',
  },
  resetBtn: {
    marginTop: 32,
    alignSelf: 'flex-start',
    paddingVertical: 12,
    paddingHorizontal: 20,
    borderRadius: 12,
    backgroundColor: '#1c1e1f',
  },
  resetBtnText: {
    color: '#ff7a7a',
    fontSize: 15,
    fontWeight: '600',
  },
  miniBtn: {
    marginTop: 24,
    paddingVertical: 10,
    paddingHorizontal: 22,
    borderRadius: 20,
    backgroundColor: '#1c1e1f',
  },
  miniBtnText: {
    color: '#cfd3d6',
    fontSize: 14,
    fontWeight: '600',
  },
});
