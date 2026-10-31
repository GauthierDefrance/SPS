<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { videos } from '../api/endpoints'
import { formatDuration } from '../lib/format'
import { useAuthImage } from '../lib/useAuthImage'

// Miniature d'une vidéo, avec sa durée. Une icône la remplace si l'image n'existe pas
// (vidéo pas encore traitée).
//   version : change l'adresse, donc force le rechargement de l'image
//   owned   : vidéo de l'utilisateur connecté, peut-être un brouillon ou une vidéo privée :
//             l'image est demandée avec le token (voir useAuthImage)

const props = defineProps<{ videoId: string; title: string; duration?: number; version?: string; owned?: boolean }>()

const url = computed(() => videos.thumbnailUrl(props.videoId) + (props.version ? '?v=' + props.version : ''))

// Vidéo visible de tous : simple <img>, chargée seulement quand elle arrive à l'écran (loading="lazy")
const publicFailed = ref(false)
watch(url, () => { publicFailed.value = false })

// Vidéo à moi : téléchargée avec le token
const privateImage = useAuthImage(() => (props.owned ? url.value : null))

const src = computed(() => (props.owned ? privateImage.src.value : url.value))
const failed = computed(() => (props.owned ? privateImage.failed.value : publicFailed.value))
</script>

<template>
  <div class="thumbnail">
    <div v-if="failed || !src" class="thumbnail-placeholder" aria-hidden="true">
      <v-icon v-if="failed" icon="fas fa-film" size="x-large" />
    </div>
    <img v-else :src="src" alt="" loading="lazy" @error="publicFailed = true">
    <span class="sr-only">{{ title }}</span>
    <span v-if="duration" class="duration">{{ formatDuration(duration) }}</span>
  </div>
</template>
