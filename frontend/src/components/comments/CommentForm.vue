<script setup lang="ts">
import { nextTick, onMounted, ref, useTemplateRef } from 'vue'
import { session } from '../../auth/session'
import { errorText } from '../../lib/useAsync'
import UserAvatar from '../UserAvatar.vue'
import { MAX_LENGTH } from './comments'

// Zone de saisie d'un commentaire ou d'une réponse.

const props = withDefaults(defineProps<{
  placeholder: string
  submitLabel: string
  send: (content: string) => Promise<void>
  initial?: string
  autofocus?: boolean
  cancelable?: boolean     // false : "Annuler" vide simplement la zone
}>(), { initial: '', autofocus: false, cancelable: false })
const emit = defineEmits<{ cancel: [] }>()

const content = ref(props.initial)
const sending = ref(false)
const error = ref<string>()
const field = useTemplateRef<{ focus: () => void }>('field')

// Zone ouverte préremplie ("@Nom ") : curseur placé à la fin
onMounted(async () => {
  if (!props.autofocus) return
  await nextTick()
  field.value?.focus()
})

async function submit() {
  sending.value = true
  error.value = undefined
  try {
    await props.send(content.value.trim())
    content.value = ''
  } catch (e) {
    error.value = errorText(e, 'Envoi impossible.')
  } finally {
    sending.value = false
  }
}

function cancel() {
  if (props.cancelable) emit('cancel')
  else content.value = ''
}
</script>

<template>
  <form class="comment-form" @submit.prevent="submit">
    <UserAvatar v-if="session.user" :user="session.user" size="small" />
    <div class="comment-body">
      <v-textarea ref="field" v-model="content" rows="1" auto-grow :maxlength="MAX_LENGTH" :placeholder="placeholder"
                  :aria-label="placeholder" :disabled="sending" variant="underlined" />
      <v-alert v-if="error" type="error" :text="error" class="mt-2" />
      <div v-if="content || cancelable" class="form-actions">
        <v-btn variant="text" :disabled="sending" @click="cancel">Annuler</v-btn>
        <v-btn type="submit" color="primary" :loading="sending" :disabled="!content.trim()">{{ submitLabel }}</v-btn>
      </div>
    </div>
  </form>
</template>
