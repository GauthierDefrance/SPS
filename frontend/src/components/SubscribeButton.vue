<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { users } from '../api/endpoints'
import { reloadUser, session } from '../auth/session'
import { notify } from '../lib/feedback'
import { errorText } from '../lib/useAsync'

// Bouton S'abonner / Abonné. Sans compte, il mène à la connexion puis revient ici.
// Masqué sur sa propre chaîne.

const props = defineProps<{ channelId: string; subscribed: boolean }>()
const emit = defineEmits<{ change: [subscribed: boolean] }>()
const route = useRoute()
const router = useRouter()
const busy = ref(false)

async function toggle() {
  if (!session.user) {
    void router.push({ path: '/login', query: { next: route.fullPath } })
    return
  }
  busy.value = true
  try {
    await (props.subscribed ? users.unsubscribe(props.channelId) : users.subscribe(props.channelId))
    emit('change', !props.subscribed)
    void reloadUser()   // compteur "abonnements" du profil
  } catch (e) {
    notify(errorText(e, 'Action impossible.'), 'error')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <v-btn v-if="session.user?.id !== channelId" :color="subscribed ? undefined : 'secondary'"
         :variant="subscribed ? 'tonal' : 'flat'" :prepend-icon="subscribed ? 'fas fa-user-check' : 'fas fa-user-plus'"
         :loading="busy" :aria-pressed="subscribed" @click="toggle">
    {{ subscribed ? 'Abonné' : 'S\'abonner' }}
  </v-btn>
</template>
