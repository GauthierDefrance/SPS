<script setup lang="ts">
import Hls from 'hls.js'
import { computed, onBeforeUnmount, onMounted, ref, useTemplateRef, watch } from 'vue'
import { getAccessToken } from '../api/client'

// Lecteur HLS avec ses propres contrôles (ceux du navigateur sont masqués) : lecture, ±15 s,
// volume, barre de progression avec aperçu du temps, qualité, vitesse, image dans l'image et
// plein écran. Les contrôles se masquent pendant la lecture quand la souris ne bouge plus.
//
// hls.js télécharge master.m3u8, puis les segments de la qualité choisie, et change de qualité
// selon le débit (mode Auto). Safari lit le HLS sans hls.js (pas de choix de qualité alors).
// Le token est ajouté aux requêtes de hls.js : nécessaire pour les vidéos privées.
//
// Raccourcis (lecteur sélectionné) : Espace/K lecture, ←/→ ±5 s, J/L ±15 s, ↑/↓ volume,
// M muet, F plein écran, 0-9 aller à 0 %-90 %.

const props = defineProps<{ src: string; poster?: string }>()
const emit = defineEmits<{
  play: []                       // une fois, au premier lancement de la lecture (compte la vue)
  progress: [seconds: number]    // régulièrement pendant la lecture et à la pause
}>()

const SKIP_SECONDS = 15
const HIDE_CONTROLS_AFTER = 2500   // ms d'inactivité avant de masquer les contrôles pendant la lecture
const SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 2]
const VOLUME_KEY = 'sps.player.volume'

/**
 * Débit estimé par le navigateur, en bit/s (Network Information API : Chrome, Edge, Android).
 * Firefox et Safari ne le donnent pas : undefined.
 */
function networkDownlink(): number | undefined {
  const connection = (navigator as Navigator & { connection?: { downlink?: number } }).connection
  return connection?.downlink ? connection.downlink * 1_000_000 : undefined   // downlink est en Mbit/s
}

/**
 * La plus petite qualité qui remplit le lecteur en pixels réels (taille CSS × densité de l'écran),
 * même règle que capLevelToPlayerSize. Utile au démarrage : hls.js ne calcule sa propre limite
 * qu'un peu plus tard. Les niveaux de hls.js sont rangés du plus léger au plus lourd.
 */
function levelForPlayer(levels: { width: number; height: number }[], video: HTMLVideoElement): number {
  const width = video.clientWidth * devicePixelRatio
  const height = video.clientHeight * devicePixelRatio
  const index = levels.findIndex((level) => level.width >= width || level.height >= height)
  return index === -1 ? levels.length - 1 : index
}

/** 83.7 -> "1:23" (arrondi vers le bas : le temps affiché ne doit pas "sauter" en avance). */
function formatTime(seconds: number): string {
  const total = Number.isFinite(seconds) ? Math.max(0, Math.floor(seconds)) : 0
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = String(total % 60).padStart(2, '0')
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${s}` : `${m}:${s}`
}

/** Volume mémorisé d'une visite à l'autre (simple confort : sans stockage, on repart à 100 %). */
function loadVolume(): { volume: number; muted: boolean } {
  try {
    const saved = JSON.parse(localStorage.getItem(VOLUME_KEY) ?? 'null')
    if (typeof saved?.volume === 'number') return { volume: Math.min(Math.max(saved.volume, 0), 1), muted: !!saved.muted }
  } catch { /* stockage indisponible ou valeur illisible */ }
  return { volume: 1, muted: false }
}

function saveVolume(volume: number, muted: boolean) {
  try {
    localStorage.setItem(VOLUME_KEY, JSON.stringify({ volume, muted }))
  } catch { /* navigation privée : tant pis */ }
}

interface Level {
  index: number
  label: string   // "720p" : petit côté de l'image, comme les qualités du backend
  height: number
}

const container = useTemplateRef<HTMLDivElement>('container')
const videoEl = useTemplateRef<HTMLVideoElement>('video')
let hls: Hls | null = null

const levels = ref<Level[]>([])
const selected = ref(-1)          // -1 = Auto
const current = ref<string | null>(null)
const error = ref<string | null>(null)

// État de la lecture, recopié depuis les événements de la balise <video>
const playing = ref(false)
const started = ref(false)
const ended = ref(false)
const waiting = ref(false)
const time = ref(0)
const duration = ref(0)
const buffered = ref(0)
const saved = loadVolume()
const volume = ref(saved.volume)
const muted = ref(saved.muted)
const speed = ref(1)
const fullscreen = ref(false)
const pipSupported = document.pictureInPictureEnabled

// Interface
const controlsVisible = ref(true)
const menu = ref<'main' | 'quality' | 'speed' | null>(null)
const hover = ref<{ x: number; time: number } | null>(null)   // aperçu sur la barre
const scrubbing = ref(false)
const flash = ref<{ side: 'left' | 'right'; text: string; id: number } | null>(null)
let hideTimer: number | undefined
let lastTap: { time: number; x: number } | null = null

// --- Chargement de la vidéo ---

function attach(src: string) {
  const video = videoEl.value
  if (!video) return
  detach()
  error.value = null
  levels.value = []
  selected.value = -1
  current.value = null
  started.value = false

  if (Hls.isSupported()) {
    const downlink = networkDownlink()
    const instance = new Hls({
      capLevelToPlayerSize: true,   // pas de 1080p dans un lecteur de 400 pixels de large
      // Débit supposé avant la première mesure. Par défaut hls.js suppose 500 kbit/s, d'où un
      // démarrage en 144p-240p même sur une bonne connexion.
      abrEwmaDefaultEstimate: downlink ?? 5_000_000,
      // On choisit nous-mêmes la qualité de départ (MANIFEST_PARSED) avant de lancer le téléchargement
      autoStartLoad: false,
      xhrSetup: (xhr) => {
        const token = getAccessToken()
        if (token) xhr.setRequestHeader('Authorization', 'Bearer ' + token)
      },
    })
    hls = instance
    instance.on(Hls.Events.MANIFEST_PARSED, () => {
      levels.value = instance.levels
        .map((level, index) => ({ index, label: Math.min(level.width, level.height) + 'p', height: Math.min(level.width, level.height) }))
        .sort((a, b) => b.height - a.height)

      // Départ : la meilleure qualité utile pour la taille du lecteur, en dessous du débit annoncé
      // par le navigateur s'il le connaît. Ensuite le mode Auto mesure le vrai débit à chaque
      // segment et baisse ou monte la qualité.
      let start = Math.min(levelForPlayer(instance.levels, video), Math.max(instance.maxAutoLevel, 0))
      if (downlink) {
        while (start > 0 && (instance.levels[start]?.bitrate ?? 0) > downlink * 0.8) start--
      }
      instance.startLevel = start
      instance.startLoad()
    })
    instance.on(Hls.Events.LEVEL_SWITCHED, (_, data) => {
      const level = instance.levels[data.level]
      if (level) current.value = Math.min(level.width, level.height) + 'p'
    })
    instance.on(Hls.Events.ERROR, (_, data) => {
      if (data.fatal) error.value = 'Lecture impossible (' + data.details + ').'
    })
    instance.loadSource(src)
    instance.attachMedia(video)
  } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
    video.src = src   // Safari : HLS natif, qualité choisie par le navigateur
  } else {
    error.value = 'Ce navigateur ne sait pas lire cette vidéo.'
  }
}

function detach() {
  hls?.destroy()
  hls = null
  videoEl.value?.removeAttribute('src')
}

// --- Événements de la balise <video> ---

let played = false
let lastSent = 0

/** Recopie l'état de la balise <video> pour l'afficher. */
function sync() {
  const video = videoEl.value
  if (!video) return
  playing.value = !video.paused
  ended.value = video.ended
  time.value = video.currentTime
  duration.value = Number.isFinite(video.duration) ? video.duration : 0
  // Partie déjà téléchargée autour de la position actuelle
  let end = 0
  for (let i = 0; i < video.buffered.length; i++) {
    if (video.buffered.start(i) <= video.currentTime + 0.5) end = Math.max(end, video.buffered.end(i))
  }
  buffered.value = end
}

function onPlay() {
  sync()
  if (!played) {
    played = true
    emit('play')
  }
}

// Position envoyée toutes les 10 s de lecture et à chaque pause
function onTimeUpdate() {
  sync()
  const position = videoEl.value?.currentTime ?? 0
  if (Math.abs(position - lastSent) >= 10) {
    lastSent = position
    emit('progress', position)
  }
}

function onPause() {
  sync()
  emit('progress', videoEl.value?.currentTime ?? 0)
}

function onPlaying() {
  started.value = true
  waiting.value = false
  sync()
}

function onFullscreenChange() {
  fullscreen.value = document.fullscreenElement === container.value
}

onMounted(() => {
  attach(props.src)
  applySound()
  document.addEventListener('fullscreenchange', onFullscreenChange)
})
onBeforeUnmount(() => {
  detach()
  window.clearTimeout(hideTimer)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
})
watch(() => props.src, (src) => {
  played = false
  lastSent = 0
  attach(src)
})

// --- Volume et vitesse ---

function applySound() {
  if (!videoEl.value) return
  videoEl.value.volume = volume.value
  videoEl.value.muted = muted.value
  saveVolume(volume.value, muted.value)
}
watch([volume, muted], applySound)
watch(speed, (value) => { if (videoEl.value) videoEl.value.playbackRate = value })

function changeVolume(value: number) {
  volume.value = value
  muted.value = value === 0
}

function changeVolumeBy(delta: number) {
  changeVolume(Math.round(Math.min(1, Math.max(0, (muted.value ? 0 : volume.value) + delta)) * 100) / 100)
}

function toggleMute() {
  if (muted.value || volume.value === 0) {
    volume.value = volume.value || 0.5
    muted.value = false
  } else {
    muted.value = true
  }
}

// --- Masquage automatique des contrôles ---

function showControls() {
  controlsVisible.value = true
  window.clearTimeout(hideTimer)
  hideTimer = window.setTimeout(() => { controlsVisible.value = false }, HIDE_CONTROLS_AFTER)
}
// La lecture démarre : le compte à rebours de masquage commence, même sans bouger la souris
watch(playing, (isPlaying) => { if (isPlaying) showControls() })
// En pause, menu ouvert ou pendant un déplacement sur la barre : les contrôles restent affichés
const controlsShown = computed(() => controlsVisible.value || !playing.value || menu.value !== null || scrubbing.value)

// --- Actions ---

function togglePlay() {
  const video = videoEl.value
  if (!video) return
  if (video.paused || video.ended) void video.play().catch(() => { /* lecture refusée par le navigateur */ })
  else video.pause()
}

function seekTo(seconds: number) {
  const video = videoEl.value
  if (!video || !duration.value) return
  video.currentTime = Math.min(Math.max(seconds, 0), duration.value)
  time.value = video.currentTime
}

function skip(seconds: number) {
  seekTo((videoEl.value?.currentTime ?? 0) + seconds)
  flash.value = { side: seconds < 0 ? 'left' : 'right', text: (seconds < 0 ? '− ' : '+ ') + Math.abs(seconds) + ' s', id: Date.now() }
  showControls()
}

function toggleFullscreen() {
  if (document.fullscreenElement) void document.exitFullscreen()
  else void container.value?.requestFullscreen().catch(() => { /* refusé (iframe, iOS...) */ })
}

function togglePip() {
  const video = videoEl.value
  if (!video) return
  if (document.pictureInPictureElement) void document.exitPictureInPicture()
  else void video.requestPictureInPicture().catch(() => { /* non disponible */ })
}

function chooseLevel(index: number) {
  selected.value = index
  if (hls) hls.currentLevel = index   // appliqué à partir du segment suivant
  menu.value = null
}

function chooseSpeed(value: number) {
  speed.value = value
  menu.value = null
}

// --- Souris / doigt sur l'image ---

// Clic : lecture / pause. Toucher : affiche d'abord les contrôles ; double toucher sur un côté : ±15 s
function onSurfaceUp(e: PointerEvent) {
  if (menu.value) { menu.value = null; return }
  if (e.pointerType !== 'touch') { togglePlay(); return }

  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const x = (e.clientX - rect.left) / rect.width
  const now = Date.now()
  if (lastTap && now - lastTap.time < 300 && (x < 0.35 || x > 0.65)) {
    skip(x < 0.5 ? -SKIP_SECONDS : SKIP_SECONDS)
    lastTap = null
    return
  }
  lastTap = { time: now, x }
  if (controlsShown.value && playing.value) controlsVisible.value = false
  else showControls()
}

function onPointerMove(e: PointerEvent) {
  if (e.pointerType !== 'touch') showControls()
}

function onPointerLeave() {
  if (playing.value) controlsVisible.value = false
}

// --- Barre de progression : survol (aperçu du temps) et déplacement ---

function timeAt(e: PointerEvent) {
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const ratio = Math.min(Math.max((e.clientX - rect.left) / rect.width, 0), 1)
  return { x: ratio * rect.width, time: ratio * duration.value }
}

function onBarDown(e: PointerEvent) {
  (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  scrubbing.value = true
  const at = timeAt(e)
  hover.value = at
  seekTo(at.time)
}

function onBarMove(e: PointerEvent) {
  const at = timeAt(e)
  hover.value = at
  if (scrubbing.value) seekTo(at.time)
}

function onBarLeave() {
  if (!scrubbing.value) hover.value = null
}

// --- Clavier ---

function onKeyDown(e: KeyboardEvent) {
  // Les champs du lecteur (curseur de volume) gardent leurs flèches
  if (e.target instanceof HTMLInputElement && e.key.startsWith('Arrow')) return
  const key = e.key.toLowerCase()
  // Espace sur un bouton : le bouton s'en charge déjà
  if (key === ' ' && e.target instanceof HTMLButtonElement) return
  const actions: Record<string, () => void> = {
    ' ': togglePlay, k: togglePlay,
    arrowleft: () => skip(-5), arrowright: () => skip(5),
    j: () => skip(-SKIP_SECONDS), l: () => skip(SKIP_SECONDS),
    arrowup: () => changeVolumeBy(0.1),
    arrowdown: () => changeVolumeBy(-0.1),
    m: toggleMute, f: toggleFullscreen,
  }
  if (/^[0-9]$/.test(key)) actions[key] = () => seekTo((duration.value * Number(key)) / 10)
  const action = actions[key]
  if (!action) return
  e.preventDefault()
  action()
  showControls()
}

// --- Affichage ---

const level = computed(() => levels.value.find((l) => l.index === selected.value))
const autoLabel = computed(() => `Auto${current.value ? ` (${current.value})` : ''}`)
const qualityText = computed(() => level.value?.label ?? autoLabel.value)
const speedText = computed(() => (speed.value === 1 ? 'Normale' : `${speed.value}×`))
const volumeIcon = computed(() => (muted.value || volume.value === 0 ? 'fas fa-volume-xmark'
  : volume.value < 0.5 ? 'fas fa-volume-low' : 'fas fa-volume-high'))
const shownVolume = computed(() => (muted.value ? 0 : volume.value))
const progress = computed(() => (duration.value ? (time.value / duration.value) * 100 : 0))
const loaded = computed(() => (duration.value ? (buffered.value / duration.value) * 100 : 0))
const playLabel = computed(() => (playing.value ? 'Pause (k)' : 'Lecture (k)'))
const muteLabel = computed(() => (muted.value ? 'Réactiver le son (m)' : 'Couper le son (m)'))
const fullscreenLabel = computed(() => (fullscreen.value ? 'Quitter le plein écran (f)' : 'Plein écran (f)'))
</script>

<template>
  <div ref="container" tabindex="0" aria-label="Lecteur vidéo" class="player"
       :class="{ idle: !controlsShown, fullscreen }"
       @keydown="onKeyDown" @pointermove="onPointerMove" @pointerleave="onPointerLeave">
    <video ref="video" playsinline :poster="poster" preload="metadata"
           @play="onPlay" @pause="onPause" @timeupdate="onTimeUpdate" @playing="onPlaying" @waiting="waiting = true"
           @durationchange="sync" @progress="sync" @ended="sync" @seeked="sync" @loadedmetadata="sync" />

    <!-- Zone de clic sur l'image (sous les contrôles) -->
    <div class="player-surface" @pointerup="onSurfaceUp" @dblclick="toggleFullscreen" />

    <!-- Gros bouton central : avant la première lecture, ou à la fin -->
    <button v-if="(!started || ended) && !error" type="button" class="player-big-play" :aria-label="ended ? 'Revoir' : 'Lire'"
            @click="togglePlay">
      <v-icon :icon="ended ? 'fas fa-rotate-right' : 'fas fa-play'" />
    </button>
    <div v-if="waiting && playing" class="player-spinner" aria-label="Chargement" />
    <div v-if="flash" :key="flash.id" class="player-flash" :class="flash.side" aria-hidden="true">{{ flash.text }}</div>
    <div v-if="error" class="player-error" role="alert">{{ error }}</div>

    <div class="player-controls" @pointerup.stop>
      <div class="player-progress" role="slider" aria-label="Position" tabindex="-1"
           :aria-valuemin="0" :aria-valuemax="Math.round(duration)" :aria-valuenow="Math.round(time)"
           :aria-valuetext="`${formatTime(time)} sur ${formatTime(duration)}`"
           @pointerdown="onBarDown" @pointermove="onBarMove" @pointerup="scrubbing = false"
           @pointercancel="scrubbing = false" @pointerleave="onBarLeave">
        <div class="track">
          <div class="buffered" :style="{ width: loaded + '%' }" />
          <div v-if="hover" class="hovered" :style="{ width: hover.x + 'px' }" />
          <div class="played" :style="{ width: progress + '%' }" />
        </div>
        <div class="thumb" :style="{ left: progress + '%' }" />
        <div v-if="hover" class="tooltip" :style="{ left: hover.x + 'px' }">{{ formatTime(hover.time) }}</div>
      </div>

      <div class="player-buttons">
        <button type="button" :aria-label="playLabel" :title="playLabel" @click="togglePlay">
          <v-icon :icon="playing ? 'fas fa-pause' : ended ? 'fas fa-rotate-right' : 'fas fa-play'" />
        </button>
        <button type="button" class="skip" aria-label="Reculer de 15 secondes (j)" title="Reculer de 15 s (j)"
                @click="skip(-SKIP_SECONDS)">
          <v-icon icon="fas fa-rotate-left" /><span>15</span>
        </button>
        <button type="button" class="skip" aria-label="Avancer de 15 secondes (l)" title="Avancer de 15 s (l)"
                @click="skip(SKIP_SECONDS)">
          <v-icon icon="fas fa-rotate-right" /><span>15</span>
        </button>

        <div class="player-volume">
          <button type="button" :aria-label="muteLabel" :title="muteLabel" @click="toggleMute">
            <v-icon :icon="volumeIcon" />
          </button>
          <input type="range" min="0" max="1" step="0.05" :value="shownVolume" aria-label="Volume"
                 :style="{ '--fill': shownVolume * 100 + '%' }"
                 @input="changeVolume(Number(($event.target as HTMLInputElement).value))">
        </div>

        <span class="player-time">{{ formatTime(time) }} <span>/ {{ formatTime(duration) }}</span></span>
        <span class="spacer" />

        <div class="player-menu-anchor">
          <button type="button" class="settings" :class="{ open: menu }" aria-haspopup="menu" :aria-expanded="menu !== null"
                  aria-label="Paramètres" title="Paramètres" @click="menu = menu ? null : 'main'">
            <v-icon icon="fas fa-gear" />
            <span v-if="level && level.height >= 720" class="hd-badge">HD</span>
          </button>
          <div v-if="menu" class="player-menu" role="menu">
            <template v-if="menu === 'main'">
              <button v-if="levels.length > 0" type="button" role="menuitem" @click="menu = 'quality'">
                <span>Qualité</span><span class="value">{{ qualityText }}</span><v-icon icon="fas fa-chevron-right" />
              </button>
              <button type="button" role="menuitem" @click="menu = 'speed'">
                <span>Vitesse</span><span class="value">{{ speedText }}</span><v-icon icon="fas fa-chevron-right" />
              </button>
            </template>
            <template v-else-if="menu === 'quality'">
              <button type="button" class="menu-back" @click="menu = 'main'"><v-icon icon="fas fa-chevron-left" /> Qualité</button>
              <button type="button" role="menuitemradio" :aria-checked="selected === -1" @click="chooseLevel(-1)">
                <span class="check"><v-icon v-if="selected === -1" icon="fas fa-check" /></span>
                <span>{{ autoLabel }}</span>
              </button>
              <button v-for="l in levels" :key="l.index" type="button" role="menuitemradio" :aria-checked="selected === l.index"
                      @click="chooseLevel(l.index)">
                <span class="check"><v-icon v-if="selected === l.index" icon="fas fa-check" /></span>
                <span>{{ l.label }}</span>
                <span v-if="l.height >= 720" class="hd-tag">HD</span>
              </button>
            </template>
            <template v-else>
              <button type="button" class="menu-back" @click="menu = 'main'"><v-icon icon="fas fa-chevron-left" /> Vitesse</button>
              <button v-for="s in SPEEDS" :key="s" type="button" role="menuitemradio" :aria-checked="speed === s"
                      @click="chooseSpeed(s)">
                <span class="check"><v-icon v-if="speed === s" icon="fas fa-check" /></span>
                <span>{{ s === 1 ? 'Normale' : `${s}×` }}</span>
              </button>
            </template>
          </div>
        </div>

        <button v-if="pipSupported" type="button" class="pip" aria-label="Image dans l'image" title="Image dans l'image"
                @click="togglePip">
          <v-icon icon="far fa-clone" />
        </button>
        <button type="button" :aria-label="fullscreenLabel" :title="fullscreenLabel" @click="toggleFullscreen">
          <v-icon :icon="fullscreen ? 'fas fa-compress' : 'fas fa-expand'" />
        </button>
      </div>
    </div>
  </div>
</template>
