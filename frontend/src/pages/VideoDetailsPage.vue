<script setup lang="ts">
import { useRouter } from 'vue-router'
import { videos } from '../api/endpoints'
import type { Video } from '../api/types'
import { session } from '../auth/session'
import AuthImg from '../components/AuthImg.vue'
import VideoEditForm from '../components/VideoEditForm.vue'
import { notify } from '../lib/feedback'
import { formatAgo, formatDuration } from '../lib/format'
import { useAsync, usePolling } from '../lib/useAsync'

// Détails d'une de mes vidéos : titre, description, visibilité et miniature. C'est ici qu'arrive
// une vidéo juste envoyée (en brouillon) : on remplit ses informations pendant le traitement,
// puis on la publie. Pour une vidéo déjà publiée, la même page sert à la modifier.

const props = defineProps<{ id: string }>()
const router = useRouter()
const { data: video, error, reload, update } = useAsync(() => videos.get(props.id), () => props.id)

// Pendant le traitement, on suit son avancement toutes les 2 s. Le formulaire garde ce qui a été saisi.
usePolling(() => video.value?.status === 'PENDING' || video.value?.status === 'PROCESSING', reload)

function saved(changed: Video) {
  update(() => changed)
  notify('Modifications enregistrées.')
}

function published(changed: Video) {
  notify('Vidéo publiée !')
  void router.push(`/watch/${changed.id}`)
}
</script>

<template>
  <v-alert v-if="error && !video" type="error" :text="error" />
  <v-progress-linear v-else-if="!video" indeterminate color="primary" />
  <v-alert v-else-if="video.owner.id !== session.user?.id" type="error" text="Seul le propriétaire peut modifier cette vidéo." />
  <section v-else>
    <div class="details-header">
      <h1>{{ video.publishedAt ? 'Modifier la vidéo' : 'Publier une vidéo' }}</h1>
      <v-chip v-if="video.publishedAt" color="success" prepend-icon="fas fa-circle-check">
        Publiée {{ formatAgo(video.publishedAt) }}
      </v-chip>
      <v-chip v-else color="warning" prepend-icon="fas fa-pen">Brouillon</v-chip>
    </div>

    <div class="with-sidebar reverse">
      <VideoEditForm :video="video" @cancel="router.push('/me')" @saved="saved" @published="published"
                     @thumbnail-change="(changed) => update((current) => ({ ...current, thumbnail: changed.thumbnail }))" />

      <v-card class="sidebar details-preview" variant="outlined">
        <AuthImg v-if="video.status === 'READY'" class="preview-image"
                 :src="`${videos.thumbnailUrl(video.id)}?v=${video.thumbnail.selected}`" alt="Miniature choisie" />
        <div v-else class="preview-image placeholder">
          <v-alert v-if="video.status === 'FAILED'" type="error"
                   :text="`Le traitement a échoué${video.processing.error ? ' : ' + video.processing.error : '.'}`" />
          <v-progress-linear v-else :model-value="video.processing.progress" color="primary" height="24" rounded>
            <small>{{ video.processing.step ?? 'En attente' }} · {{ video.processing.progress }} %</small>
          </v-progress-linear>
        </div>
        <dl class="details-facts">
          <dt>Lien de la vidéo</dt>
          <dd><router-link :to="`/watch/${video.id}`">/watch/{{ video.id }}</router-link></dd>
          <dt>Traitement</dt>
          <dd>
            <template v-if="video.status === 'READY'">
              Terminé · {{ formatDuration(video.duration) }} · jusqu'en {{ video.qualities[video.qualities.length - 1] ?? '?' }}
            </template>
            <template v-else-if="video.status === 'FAILED'">Échec</template>
            <template v-else>En cours ({{ video.processing.progress }} %)</template>
          </dd>
          <dt>Envoyée</dt>
          <dd>{{ formatAgo(video.createdAt) }}</dd>
        </dl>
      </v-card>
    </div>
  </section>
</template>
