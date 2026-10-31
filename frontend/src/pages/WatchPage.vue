<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { users, videos } from '../api/endpoints'
import { reloadUser, session } from '../auth/session'
import ReactionButtons from '../components/ReactionButtons.vue'
import SubscribeButton from '../components/SubscribeButton.vue'
import UserAvatar from '../components/UserAvatar.vue'
import VideoPlayer from '../components/VideoPlayer.vue'
import CommentSection from '../components/comments/CommentSection.vue'
import { confirm, notify } from '../lib/feedback'
import { formatAgo, formatCount, formatViews, fullName } from '../lib/format'
import { errorText, useAsync, usePolling } from '../lib/useAsync'
import { useAuthImage } from '../lib/useAuthImage'

// Une vidéo : lecteur, titre, réactions, chaîne, description et commentaires. Son propriétaire
// peut la supprimer, ou la modifier sur la page de détails (/me/videos/:id).

const props = defineProps<{ id: string }>()
const router = useRouter()

// La réponse dépend de l'utilisateur (sa réaction, l'accès aux vidéos privées) : on recharge s'il change
const { data: video, error, reload, update } = useAsync(() => videos.get(props.id), () => [props.id, session.user?.id])
const ownerId = computed(() => video.value?.owner.id)
const { data: channel, update: updateChannel } = useAsync(
  () => (ownerId.value ? users.profile(ownerId.value) : Promise.resolve(null)), () => [ownerId.value, session.user?.id])

const isOwner = computed(() => !!session.user && session.user.id === ownerId.value)

// Image affichée avant la lecture. Brouillon ou vidéo privée : seul le propriétaire la voit,
// l'image est donc demandée avec son token (une simple adresse dans <video poster> échouerait).
const posterUrl = computed(() => video.value && `${videos.thumbnailUrl(video.value.id)}?v=${video.value.thumbnail.selected}`)
const restricted = computed(() => !!video.value && (video.value.policy === 'PRIVATE' || !video.value.publishedAt))
const privatePoster = useAuthImage(() => (restricted.value ? posterUrl.value ?? null : null))
const poster = computed(() => (restricted.value ? privatePoster.src.value : posterUrl.value ?? undefined))
const deleting = ref(false)

// Vidéo pas encore prête : on vérifie toutes les 2 s où en est le traitement
usePolling(() => video.value?.status === 'PENDING' || video.value?.status === 'PROCESSING', reload)

function saveWatchPosition(seconds: number) {
  if (session.user) void users.saveWatchPosition(props.id, seconds)
}

function subscribed(value: boolean) {
  updateChannel((c) => c && { ...c, subscribed: value, subscribers: c.subscribers + (value ? 1 : -1) })
}

async function remove() {
  if (!video.value) return
  const ok = await confirm({
    title: `Supprimer « ${video.value.title} » ?`,
    text: 'La vidéo, ses commentaires et ses réactions seront définitivement supprimés.',
    confirmText: 'Supprimer',
    danger: true,
  })
  if (!ok) return
  deleting.value = true
  try {
    await videos.remove(video.value.id)
    void reloadUser()   // le compteur de vidéos du profil change
    notify('Vidéo supprimée.')
    void router.replace('/me')
  } catch (e) {
    notify(errorText(e, 'Suppression impossible.'), 'error')
    deleting.value = false
  }
}
</script>

<template>
  <v-alert v-if="error && !video" type="error" :text="error" />
  <v-progress-linear v-else-if="!video" indeterminate color="primary" />
  <article v-else class="watch">
    <VideoPlayer v-if="video.status === 'READY' && video.hlsUrl" :src="video.hlsUrl"
                 :poster="poster"
                 @play="videos.view(video.id)" @progress="saveWatchPosition" />
    <div v-else class="player placeholder">
      <v-alert v-if="video.status === 'FAILED'" type="error"
               :text="`Cette vidéo n'a pas pu être traitée${video.processing.error ? ' : ' + video.processing.error : '.'}`" />
      <div v-else>
        <p>{{ video.status === 'PENDING' ? 'En attente de traitement' : 'Traitement en cours' }} · {{ video.processing.step ?? '' }}</p>
        <v-progress-linear :model-value="video.processing.progress" color="primary" height="24" rounded>
          <strong>{{ video.processing.progress }} %</strong>
        </v-progress-linear>
      </div>
    </div>

    <v-alert v-if="!video.publishedAt" type="warning" class="mt-3" icon="fas fa-pen">
      Brouillon : cette vidéo n'est visible que par toi.
      <router-link :to="`/me/videos/${video.id}`" class="strong">Terminer et publier</router-link>
    </v-alert>
    <h1 class="watch-title">{{ video.title }}</h1>

    <div class="watch-bar">
      <div class="channel-mini">
        <router-link :to="`/channel/${video.owner.id}`" aria-hidden="true" tabindex="-1">
          <UserAvatar :user="video.owner" size="small" />
        </router-link>
        <div>
          <router-link :to="`/channel/${video.owner.id}`" class="strong">{{ fullName(video.owner) }}</router-link>
          <p v-if="channel" class="muted">
            {{ formatCount(channel.subscribers) }} abonné{{ channel.subscribers > 1 ? 's' : '' }}
          </p>
        </div>
        <SubscribeButton v-if="channel" :channel-id="video.owner.id" :subscribed="channel.subscribed" @change="subscribed" />
      </div>
      <div class="watch-actions">
        <ReactionButtons :likes="video.likes" :dislikes="video.dislikes" :my-reaction="video.myReaction"
                         :react="(type) => videos.react(video!.id, type)" :remove-reaction="() => videos.removeReaction(video!.id)"
                         @change="(r) => update((current) => ({ ...current, ...r }))" />
        <template v-if="isOwner">
          <v-btn :to="`/me/videos/${video.id}`" variant="tonal" prepend-icon="fas fa-pen">Modifier</v-btn>
          <v-btn variant="tonal" color="error" prepend-icon="far fa-trash-can" :loading="deleting" @click="remove">
            Supprimer
          </v-btn>
        </template>
      </div>
    </div>

    <div class="description">
      <p class="strong">
        {{ formatViews(video.views) }} · {{ formatAgo(video.publishedAt ?? video.createdAt) }}
        <template v-if="video.policy !== 'PUBLIC'">
          · <v-chip :prepend-icon="video.policy === 'PRIVATE' ? 'fas fa-eye-slash' : 'fas fa-share-from-square'">
            {{ video.policy === 'PRIVATE' ? 'Privée' : 'Non répertoriée' }}
          </v-chip>
        </template>
        <span v-if="video.qualities.length > 0" class="muted"> · jusqu'en {{ video.qualities[video.qualities.length - 1] }}</span>
      </p>
      <p v-if="video.description" class="pre-line">{{ video.description }}</p>
      <p v-else class="muted">Pas de description.</p>
    </div>

    <CommentSection :video-id="video.id" :video-owner-id="video.owner.id" />
  </article>
</template>
