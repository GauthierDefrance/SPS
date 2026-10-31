<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { session } from '../auth/session'
import AvatarEditor from '../components/AvatarEditor.vue'
import LikedVideos from '../components/profile/LikedVideos.vue'
import MyVideos from '../components/profile/MyVideos.vue'
import WatchHistory from '../components/profile/WatchHistory.vue'
import { formatCount, fullName } from '../lib/format'

// Mon profil : mes informations, l'envoi de vidéos, mes vidéos, mes "j'aime" et mon historique.
// L'onglet ouvert est dans l'URL (?tab=likes) : il survit à un rechargement de la page.

type Tab = 'videos' | 'likes' | 'history'

const route = useRoute()
const router = useRouter()
const tab = computed<Tab>({
  get: () => (route.query.tab === 'likes' || route.query.tab === 'history' ? route.query.tab : 'videos'),
  set: (value) => { void router.replace({ query: value === 'videos' ? {} : { tab: value } }) },
})
const plural = (n: number, word: string) => `${formatCount(n)} ${word}${n > 1 ? 's' : ''}`
</script>

<template>
  <section v-if="session.user">
    <div class="channel-header">
      <AvatarEditor />
      <div>
        <h1>{{ fullName(session.user) }}</h1>
        <p class="muted">
          {{ session.user.email }} · {{ plural(session.user.subscribers, 'abonné') }} ·
          {{ plural(session.user.subscriptions, 'abonnement') }} · {{ plural(session.user.videos, 'vidéo') }}
        </p>
      </div>
      <v-btn :to="`/channel/${session.user.id}`" variant="tonal" prepend-icon="fas fa-user" class="ms-auto">
        Voir ma chaîne publique
      </v-btn>
    </div>

    <v-tabs v-model="tab" color="primary" class="mb-6">
      <v-tab value="videos" prepend-icon="fas fa-film">Mes vidéos</v-tab>
      <v-tab value="likes" prepend-icon="far fa-thumbs-up">J'aime</v-tab>
      <v-tab value="history" prepend-icon="fas fa-clock-rotate-left">Historique</v-tab>
    </v-tabs>

    <MyVideos v-if="tab === 'videos'" />
    <LikedVideos v-else-if="tab === 'likes'" />
    <WatchHistory v-else />
  </section>
</template>
