<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { comments } from '../../api/endpoints'
import type { Comment } from '../../api/types'
import { session } from '../../auth/session'
import { confirm, notify } from '../../lib/feedback'
import { formatAgo, fullName } from '../../lib/format'
import { errorText } from '../../lib/useAsync'
import ReactionButtons from '../ReactionButtons.vue'
import UserAvatar from '../UserAvatar.vue'
import { MAX_LENGTH } from './comments'

// Un commentaire ou une réponse : auteur, texte, réactions et actions.
// Le "slot" par défaut affiche ce qui vient dessous (zone de réponse, réponses du fil).

const props = defineProps<{ comment: Comment; videoOwnerId: string }>()
const emit = defineEmits<{ change: [comment: Comment]; deleted: []; reply: [] }>()
const route = useRoute()

const draft = ref<string | null>(null)   // non null : en cours de modification
const busy = ref(false)
const canEdit = computed(() => session.user?.id === props.comment.author.id)
// L'auteur de la vidéo modère les commentaires sous ses vidéos
const canDelete = computed(() => canEdit.value || session.user?.id === props.videoOwnerId)
const name = computed(() => fullName(props.comment.author))

async function run(action: () => Promise<void>) {
  busy.value = true
  try {
    await action()
  } catch (e) {
    notify(errorText(e, 'Action impossible.'), 'error')
  } finally {
    busy.value = false
  }
}

function save() {
  const content = draft.value?.trim()
  if (!content) return
  void run(async () => {
    emit('change', await comments.edit(props.comment.id, content))
    draft.value = null
  })
}

async function remove() {
  const ok = await confirm({
    title: props.comment.replyCount > 0 ? 'Supprimer ce commentaire et ses réponses ?' : 'Supprimer ce commentaire ?',
    confirmText: 'Supprimer',
    danger: true,
  })
  if (!ok) return
  void run(async () => {
    await comments.remove(props.comment.id)
    emit('deleted')
  })
}
</script>

<template>
  <li class="comment" :class="{ reply: comment.parentId }">
    <UserAvatar :user="comment.author" size="small" />
    <div class="comment-body">
      <p class="comment-meta">
        <router-link v-if="comment.byVideoOwner" :to="`/channel/${comment.author.id}`" class="owner-badge"
                     title="Auteur de la vidéo">
          {{ name }} <v-icon icon="fas fa-circle-check" size="x-small" />
        </router-link>
        <router-link v-else :to="`/channel/${comment.author.id}`" class="strong">{{ name }}</router-link>
        {{ ' ' }}<span class="muted">{{ formatAgo(comment.createdAt) }}{{ comment.editedAt ? ' (modifié)' : '' }}</span>
      </p>

      <p v-if="draft === null" class="pre-line">{{ comment.content }}</p>
      <form v-else @submit.prevent="save">
        <v-textarea v-model="draft" rows="2" auto-grow :maxlength="MAX_LENGTH" :disabled="busy"
                    aria-label="Modifier le commentaire" autofocus />
        <div class="form-actions">
          <v-btn variant="text" :disabled="busy" @click="draft = null">Annuler</v-btn>
          <v-btn type="submit" color="primary" :loading="busy" :disabled="!draft.trim()">Enregistrer</v-btn>
        </div>
      </form>

      <div class="comment-actions">
        <ReactionButtons compact :likes="comment.likes" :dislikes="comment.dislikes" :my-reaction="comment.myReaction"
                         :react="(type) => comments.react(comment.id, type)"
                         :remove-reaction="() => comments.removeReaction(comment.id)"
                         @change="(r) => emit('change', { ...comment, ...r })" />
        <v-btn v-if="session.user" size="small" variant="text" prepend-icon="fas fa-reply" @click="emit('reply')">
          Répondre
        </v-btn>
        <v-btn v-else size="small" variant="text" prepend-icon="fas fa-reply"
               :to="{ path: '/login', query: { next: route.fullPath } }">
          Répondre
        </v-btn>
        <v-btn v-if="canEdit && draft === null" icon="fas fa-pen" size="small" variant="text" title="Modifier"
               aria-label="Modifier" :disabled="busy" @click="draft = comment.content" />
        <v-btn v-if="canDelete" icon="far fa-trash-can" size="small" variant="text" title="Supprimer"
               aria-label="Supprimer" :disabled="busy" @click="remove" />
      </div>
      <slot />
    </div>
  </li>
</template>
