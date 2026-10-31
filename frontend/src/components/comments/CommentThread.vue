<script setup lang="ts">
import { ref } from 'vue'
import { comments } from '../../api/endpoints'
import type { Comment } from '../../api/types'
import { formatCount, fullName } from '../../lib/format'
import { errorText } from '../../lib/useAsync'
import CommentForm from './CommentForm.vue'
import CommentItem from './CommentItem.vue'
import { appendPage } from './comments'

// Un commentaire principal et ses réponses (un seul niveau, comme sur YouTube). Répondre à une
// réponse ajoute la réponse dans le même fil, préremplie avec "@Nom" pour savoir à qui on répond.

const props = defineProps<{ comment: Comment; videoOwnerId: string }>()
const emit = defineEmits<{
  change: [comment: Comment]
  deleted: []
  countChange: [delta: number]   // nombre total de commentaires affiché en en-tête
}>()

const replies = ref<Comment[]>([])
const nextPage = ref<number | null>(0)   // prochaine page de réponses à charger ; null : tout est chargé
const open = ref(false)
const replyingTo = ref<Comment | null>(null)
const loading = ref(false)
const error = ref<string>()

async function loadReplies() {
  if (nextPage.value === null) return
  loading.value = true
  error.value = undefined
  try {
    const page = await comments.replies(props.comment.id, nextPage.value)
    replies.value = appendPage(replies.value, page.items)
    nextPage.value = page.page + 1 < page.totalPages ? page.page + 1 : null
  } catch (e) {
    error.value = errorText(e, 'Chargement impossible.')
  } finally {
    loading.value = false
  }
}

function toggle() {
  if (!open.value && replies.value.length === 0) void loadReplies()
  open.value = !open.value
}

async function sendReply(content: string) {
  if (!replyingTo.value) return
  const reply = await comments.reply(replyingTo.value.id, content)
  // Fil pas encore déplié : on charge d'abord les réponses existantes pour garder l'ordre.
  // Aucune réponse avant celle-ci : il n'y a rien d'autre à charger.
  if (props.comment.replyCount === 0) nextPage.value = null
  else if (!open.value && replies.value.length === 0) await loadReplies()
  replies.value = appendPage(replies.value, [reply])
  open.value = true
  replyingTo.value = null
  emit('change', { ...props.comment, replyCount: props.comment.replyCount + 1 })
  emit('countChange', 1)
}

function replaceReply(changed: Comment) {
  replies.value = replies.value.map((c) => (c.id === changed.id ? changed : c))
}

function removeReply(reply: Comment) {
  replies.value = replies.value.filter((c) => c.id !== reply.id)
  emit('change', { ...props.comment, replyCount: props.comment.replyCount - 1 })
  emit('countChange', -1)
}
</script>

<template>
  <CommentItem :comment="comment" :video-owner-id="videoOwnerId" @change="emit('change', $event)"
               @deleted="emit('deleted')" @reply="replyingTo = comment">
    <CommentForm v-if="replyingTo?.id === comment.id" autofocus cancelable placeholder="Ajouter une réponse…"
                 submit-label="Répondre" :send="sendReply" @cancel="replyingTo = null" />

    <v-btn v-if="comment.replyCount > 0" variant="text" color="primary" size="small" class="replies-toggle"
           :append-icon="open ? 'fas fa-chevron-up' : 'fas fa-chevron-down'" :aria-expanded="open" @click="toggle">
      {{ open ? 'Masquer' : 'Afficher' }}
      {{ comment.replyCount > 1 ? `les ${formatCount(comment.replyCount)} réponses` : 'la réponse' }}
    </v-btn>
    <v-alert v-if="error" type="error" :text="error" class="mt-2" />

    <ul v-if="open" class="comment-list replies">
      <CommentItem v-for="reply in replies" :key="reply.id" :comment="reply" :video-owner-id="videoOwnerId"
                   @change="replaceReply" @deleted="removeReply(reply)" @reply="replyingTo = reply">
        <CommentForm v-if="replyingTo?.id === reply.id" autofocus cancelable placeholder="Ajouter une réponse…"
                     submit-label="Répondre" :initial="`@${fullName(reply.author)} `" :send="sendReply"
                     @cancel="replyingTo = null" />
      </CommentItem>
      <li v-if="nextPage !== null && replies.length > 0">
        <v-btn variant="text" color="primary" size="small" prepend-icon="fas fa-reply" :loading="loading"
               @click="loadReplies">
          Afficher plus de réponses
        </v-btn>
      </li>
    </ul>
  </CommentItem>
</template>
