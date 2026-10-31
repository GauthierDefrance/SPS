<script setup lang="ts">
import { ref, useTemplateRef } from 'vue'
import { videos } from '../api/endpoints'
import type { ThumbnailChoice, Video } from '../api/types'
import { errorText } from '../lib/useAsync'
import AuthImg from './AuthImg.vue'

// Choix de la miniature : les images extraites de la vidéo par le worker, l'image envoyée par le
// propriétaire s'il y en a une, et un bouton pour en envoyer une. Un clic suffit : le choix est
// enregistré tout de suite, sans attendre le bouton "Enregistrer" du formulaire.
// Les images sont demandées avec le token (AuthImg) : la vidéo peut être un brouillon.

const props = defineProps<{ video: Video }>()
const emit = defineEmits<{ change: [video: Video] }>()

const LABELS: Record<ThumbnailChoice, string> = {
  AUTO: 'Automatique', AUTO_1: 'Début', AUTO_2: 'Milieu', AUTO_3: 'Fin', CUSTOM: 'Personnalisée',
}
const ACCEPTED = 'image/jpeg,image/png,image/gif,image/bmp'   // formats que le backend sait lire
const MAX_SIZE = 10 * 1024 * 1024                              // comme ImageService.MAX_FILE_SIZE

const input = useTemplateRef<HTMLInputElement>('input')
const busy = ref(false)
const error = ref<string>()
// Change à chaque envoi : l'aperçu de l'image personnalisée (même adresse) est rechargé
const customVersion = ref('')

async function run(action: () => Promise<Video>) {
  busy.value = true
  error.value = undefined
  try {
    emit('change', await action())
  } catch (e) {
    error.value = errorText(e, 'Action impossible.')
  } finally {
    busy.value = false
  }
}

function choose(choice: ThumbnailChoice) {
  if (choice !== props.video.thumbnail.selected) void run(() => videos.chooseThumbnail(props.video.id, choice))
}

function upload(file: File | undefined) {
  if (input.value) input.value.value = ''   // permet de reprendre le même fichier ensuite
  if (!file) return
  if (file.size > MAX_SIZE) {
    error.value = 'L\'image dépasse 10 Mo.'
    return
  }
  void run(async () => {
    const updated = await videos.uploadThumbnail(props.video.id, file)
    customVersion.value = String(Date.now())
    return updated
  })
}
</script>

<template>
  <fieldset class="thumbnail-picker" :disabled="busy">
    <legend>Miniature</legend>
    <p v-if="video.thumbnail.options.length === 0" class="muted small-text">
      Les images extraites de la vidéo seront proposées à la fin du traitement.
    </p>
    <div class="thumbnail-options" role="radiogroup" aria-label="Miniature">
      <button v-for="choice in video.thumbnail.options" :key="choice" type="button" role="radio"
              :aria-checked="video.thumbnail.selected === choice" class="thumbnail-option"
              :class="{ selected: video.thumbnail.selected === choice }" @click="choose(choice)">
        <AuthImg :src="videos.thumbnailOptionUrl(video.id, choice, choice === 'CUSTOM' ? customVersion : '')" />
        <span>
          <v-icon v-if="video.thumbnail.selected === choice" icon="fas fa-circle-check" size="x-small" />
          {{ LABELS[choice] }}
        </span>
      </button>
      <button type="button" class="thumbnail-option upload" @click="input?.click()">
        <span class="upload-icon">
          <v-progress-circular v-if="busy" indeterminate size="28" />
          <v-icon v-else icon="fas fa-image" size="large" />
        </span>
        <span>{{ video.thumbnail.options.includes('CUSTOM') ? 'Remplacer mon image' : 'Importer une image' }}</span>
      </button>
    </div>
    <input ref="input" type="file" :accept="ACCEPTED" hidden
           @change="upload(($event.target as HTMLInputElement).files?.[0])">
    <p class="muted small-text">JPEG, PNG, GIF ou BMP, 10 Mo au plus. Recadrée en 16:9 (idéalement 1280 × 720).</p>
    <v-alert v-if="error" type="error" :text="error" />
  </fieldset>
</template>
