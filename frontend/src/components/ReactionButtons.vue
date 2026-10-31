<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { ReactionResponse, ReactionType } from '../api/types'
import { session } from '../auth/session'
import { notify } from '../lib/feedback'
import { formatCount } from '../lib/format'
import { errorText } from '../lib/useAsync'

// J'aime / Je n'aime pas, pour une vidéo comme pour un commentaire : react et removeReaction
// sont les appels d'API correspondants. Cliquer sur la réaction déjà choisie la retire ; cliquer
// sur l'autre la remplace. Les compteurs affichés sont ceux renvoyés par le backend après l'action.

const props = defineProps<{
  likes: number
  dislikes: number
  myReaction: ReactionType | null
  react: (type: ReactionType) => Promise<ReactionResponse>
  removeReaction: () => Promise<ReactionResponse>
  compact?: boolean     // version discrète, sous un commentaire
}>()
const emit = defineEmits<{ change: [result: ReactionResponse] }>()
const route = useRoute()
const router = useRouter()
const busy = ref(false)

async function click(type: ReactionType) {
  if (!session.user) {
    void router.push({ path: '/login', query: { next: route.fullPath } })
    return
  }
  busy.value = true
  try {
    emit('change', await (props.myReaction === type ? props.removeReaction() : props.react(type)))
  } catch (e) {
    notify(errorText(e, 'Action impossible.'), 'error')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <v-btn-group :density="compact ? 'compact' : 'comfortable'" :variant="compact ? 'text' : 'tonal'" rounded="pill"
               divided role="group" aria-label="Réactions">
    <v-btn :prepend-icon="myReaction === 'LIKE' ? 'fas fa-thumbs-up' : 'far fa-thumbs-up'" :disabled="busy"
           :size="compact ? 'small' : 'default'" :aria-pressed="myReaction === 'LIKE'"
           :title="myReaction === 'LIKE' ? 'Retirer mon j\'aime' : 'J\'aime'" @click="click('LIKE')">
      {{ formatCount(likes) }}
    </v-btn>
    <v-btn :prepend-icon="myReaction === 'DISLIKE' ? 'fas fa-thumbs-down' : 'far fa-thumbs-down'" :disabled="busy"
           :size="compact ? 'small' : 'default'" :aria-pressed="myReaction === 'DISLIKE'"
           :title="myReaction === 'DISLIKE' ? 'Retirer mon je n\'aime pas' : 'Je n\'aime pas'" @click="click('DISLIKE')">
      {{ formatCount(dislikes) }}
    </v-btn>
  </v-btn-group>
</template>
