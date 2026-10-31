<script setup lang="ts">
import { videos } from '../../api/endpoints'
import type { OwnedVideo, TranscodeStatus, VideoPolicy } from '../../api/types'
import { reloadUser } from '../../auth/session'
import { confirm, notify } from '../../lib/feedback'
import { formatAgo, formatDuration, formatViews } from '../../lib/format'
import { errorText } from '../../lib/useAsync'
import VideoThumbnail from '../VideoThumbnail.vue'

// Une de mes vidéos dans "Mes vidéos" : état (brouillon, traitement, en ligne), visibilité et actions.

const props = defineProps<{ video: OwnedVideo }>()
const emit = defineEmits<{ changed: [] }>()

const STATUS: Record<TranscodeStatus, { label: string; color: string; icon: string }> = {
  PENDING: { label: 'En attente', color: 'warning', icon: 'fas fa-clock-rotate-left' },
  PROCESSING: { label: 'En traitement', color: 'warning', icon: 'fas fa-gear' },
  READY: { label: 'En ligne', color: 'success', icon: 'fas fa-circle-check' },
  FAILED: { label: 'Échec', color: 'error', icon: 'fas fa-triangle-exclamation' },
}
const POLICY: Record<VideoPolicy, { label: string; icon: string }> = {
  PUBLIC: { label: 'Publique', icon: 'fas fa-compass' },
  UNLISTED: { label: 'Non répertoriée', icon: 'fas fa-share-from-square' },
  PRIVATE: { label: 'Privée', icon: 'fas fa-eye-slash' },
}

async function retry() {
  try {
    await videos.retry(props.video.publicId)
    emit('changed')
  } catch (e) {
    notify(errorText(e, 'Relance impossible.'), 'error')
  }
}

async function remove() {
  const ok = await confirm({ title: `Supprimer définitivement « ${props.video.title} » ?`, confirmText: 'Supprimer', danger: true })
  if (!ok) return
  try {
    await videos.remove(props.video.publicId)
    notify('Vidéo supprimée.')
    emit('changed')
    void reloadUser()
  } catch (e) {
    notify(errorText(e, 'Suppression impossible.'), 'error')
  }
}
</script>

<template>
  <li class="owned">
    <router-link :to="`/watch/${video.publicId}`" class="owned-thumb">
      <!-- key : la miniature n'existe qu'une fois la vidéo prête. Changer de statut recrée le
           composant, qui retente alors de charger l'image au lieu de garder l'échec précédent. -->
      <VideoThumbnail :key="video.status" owned :video-id="video.publicId" :title="video.title"
                      :duration="video.status === 'READY' ? video.duration : undefined" />
    </router-link>
    <div class="owned-body">
      <router-link :to="`/watch/${video.publicId}`" class="card-title">{{ video.title }}</router-link>
      <div class="owned-meta">
        <v-chip v-if="video.publishedAt" :color="STATUS[video.status].color" :prepend-icon="STATUS[video.status].icon">
          {{ STATUS[video.status].label }}
        </v-chip>
        <v-chip v-else color="warning" prepend-icon="fas fa-pen">
          Brouillon{{ video.status !== 'READY' ? ' · ' + STATUS[video.status].label.toLowerCase() : '' }}
        </v-chip>
        <v-chip :prepend-icon="POLICY[video.policy].icon">{{ POLICY[video.policy].label }}</v-chip>
        <span class="muted">
          <template v-if="video.status === 'READY'">{{ formatViews(video.views) }} · {{ formatDuration(video.duration) }} · </template>
          {{ video.publishedAt ? 'publiée ' + formatAgo(video.publishedAt) : 'envoyée ' + formatAgo(video.createdAt) }}
        </span>
      </div>
      <v-progress-linear v-if="video.status === 'PENDING' || video.status === 'PROCESSING'"
                         :model-value="video.processing.progress" color="primary" height="18" rounded class="owned-progress">
        <small>{{ video.processing.step ?? 'En attente' }} · {{ video.processing.progress }} %</small>
      </v-progress-linear>
      <v-alert v-if="video.status === 'FAILED'" type="error" class="mt-2">
        {{ video.processing.error ?? 'Le traitement a échoué.' }}
        <template #append>
          <v-btn size="small" variant="text" prepend-icon="fas fa-rotate-right" @click="retry">Relancer</v-btn>
        </template>
      </v-alert>
      <div class="row-actions">
        <v-btn :to="`/me/videos/${video.publicId}`" size="small" variant="tonal"
               :color="video.publishedAt ? undefined : 'primary'" prepend-icon="fas fa-pen">
          {{ video.publishedAt ? 'Modifier' : 'Terminer et publier' }}
        </v-btn>
        <v-btn size="small" variant="text" color="error" prepend-icon="far fa-trash-can" @click="remove">Supprimer</v-btn>
      </div>
    </div>
  </li>
</template>
