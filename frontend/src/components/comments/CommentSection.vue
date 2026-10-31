<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { comments } from '../../api/endpoints'
import type { Comment, CommentSort } from '../../api/types'
import { session } from '../../auth/session'
import { formatCount } from '../../lib/format'
import { errorText } from '../../lib/useAsync'
import CommentForm from './CommentForm.vue'
import CommentThread from './CommentThread.vue'
import { appendPage } from './comments'

// Les commentaires sous une vidéo. Les pages suivantes s'ajoutent à la liste ("Afficher plus").
// Chaque commentaire principal est un fil : ses réponses se chargent quand on les déplie.
// Après une action (ajout, modification, suppression, like), la liste est modifiée sur place
// avec la réponse du backend, sans tout recharger.

const props = defineProps<{ videoId: string; videoOwnerId: string }>()
const route = useRoute()

const SORTS = [{ title: 'Plus récents', value: 'RECENT' }, { title: 'Les plus aimés', value: 'TOP' }]
const sort = ref<CommentSort>('RECENT')
const items = ref<Comment[] | null>(null)
const total = ref(0)                     // tous les commentaires, réponses comprises (en-tête)
const nextPage = ref<number | null>(null)
const loading = ref(false)
const error = ref<string>()

async function load(page: number) {
  loading.value = true
  error.value = undefined
  try {
    const result = await comments.list(props.videoId, sort.value, page)
    items.value = page === 0 ? result.items : appendPage(items.value ?? [], result.items)
    total.value = result.totalComments
    nextPage.value = result.page + 1 < result.totalPages ? result.page + 1 : null
  } catch (e) {
    error.value = errorText(e, 'Chargement impossible.')
  } finally {
    loading.value = false
  }
}

// Première page ; rechargée si la vidéo, le tri ou l'utilisateur (ses réactions) change
watch(() => [props.videoId, sort.value, session.user?.id], () => load(0), { immediate: true })

async function add(content: string) {
  const comment = await comments.create(props.videoId, content)
  items.value = [comment, ...(items.value ?? [])]
  total.value++
}

function replace(comment: Comment) {
  items.value = items.value?.map((c) => (c.id === comment.id ? comment : c)) ?? null
}

// Supprimer un commentaire principal supprime aussi ses réponses
function remove(comment: Comment) {
  items.value = items.value?.filter((c) => c.id !== comment.id) ?? null
  total.value -= 1 + comment.replyCount
}
</script>

<template>
  <section class="comments" aria-label="Commentaires">
    <div class="comments-header">
      <h2>{{ items ? `${formatCount(total)} commentaire${total > 1 ? 's' : ''}` : 'Commentaires' }}</h2>
      <v-select v-model="sort" :items="SORTS" label="Trier par" density="compact" class="comments-sort" />
    </div>

    <CommentForm v-if="session.user" placeholder="Ajouter un commentaire…" submit-label="Commenter" :send="add" />
    <p v-else class="muted">
      <router-link :to="{ path: '/login', query: { next: route.fullPath } }">Connecte-toi</router-link> pour commenter.
    </p>

    <v-alert v-if="error" type="error" :text="error" class="mb-4" />
    <p v-if="items?.length === 0" class="muted">Aucun commentaire pour l'instant.</p>
    <ul class="comment-list">
      <CommentThread v-for="comment in items" :key="comment.id" :comment="comment" :video-owner-id="videoOwnerId"
                     @change="replace" @deleted="remove(comment)" @count-change="total += $event" />
    </ul>
    <p v-if="nextPage !== null" class="center-small">
      <v-btn variant="tonal" :loading="loading" @click="load(nextPage)">Afficher plus de commentaires</v-btn>
    </p>
  </section>
</template>
