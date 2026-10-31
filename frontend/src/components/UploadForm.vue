<script setup lang="ts">
import { ref, useTemplateRef } from 'vue'
import { useRouter } from 'vue-router'
import { videos } from '../api/endpoints'
import { reloadUser } from '../auth/session'
import { errorText } from '../lib/useAsync'

// Envoi d'une vidéo : il suffit de choisir le fichier. La vidéo est créée en brouillon (titre tiré
// du nom du fichier), puis on ouvre sa page de détails : on y complète titre, description,
// visibilité et miniature pendant le traitement, et on la publie quand tout est prêt.

const MAX_SIZE = 2 * 1024 * 1024 * 1024   // 2 Go, comme spring.servlet.multipart.max-file-size

const router = useRouter()
const input = useTemplateRef<HTMLInputElement>('input')
const progress = ref<number | null>(null)
const error = ref<string>()

async function send(file: File | undefined) {
  if (input.value) input.value.value = ''
  if (!file) return
  if (!file.type.startsWith('video/')) {
    error.value = 'Ce fichier n\'est pas une vidéo.'
    return
  }
  if (file.size > MAX_SIZE) {
    error.value = 'La vidéo dépasse 2 Go.'
    return
  }

  error.value = undefined
  progress.value = 0
  try {
    const title = file.name.replace(/\.[^.]+$/, '').replace(/[_-]+/g, ' ').trim().slice(0, 255) || 'Nouvelle vidéo'
    const video = await videos.upload({ title, file }, (percent) => { progress.value = percent })
    void reloadUser()   // le compteur de vidéos du profil change
    void router.push(`/me/videos/${video.id}`)
  } catch (e) {
    error.value = errorText(e, 'L\'envoi a échoué.')
    progress.value = null
  }
}
</script>

<template>
  <v-card class="upload" variant="outlined" title="Importer une vidéo" prepend-icon="fas fa-cloud-arrow-up">
    <v-card-text>
      <p class="muted small-text">
        Elle reste en brouillon, visible par toi seul : tu choisis son titre, sa miniature et sa
        visibilité avant de la publier.
      </p>
      <input ref="input" type="file" accept="video/*" hidden @change="send(($event.target as HTMLInputElement).files?.[0])">
      <v-progress-linear v-if="progress !== null" :model-value="progress" color="primary" height="24" rounded
                         role="progressbar" :aria-valuenow="progress" aria-valuemin="0" aria-valuemax="100">
        <strong>Envoi : {{ progress }} %</strong>
      </v-progress-linear>
      <v-btn v-else color="primary" block prepend-icon="fas fa-plus" @click="input?.click()">Choisir un fichier</v-btn>
      <v-alert v-if="error" type="error" :text="error" class="mt-3" />
    </v-card-text>
  </v-card>
</template>
