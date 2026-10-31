<script setup lang="ts">
import { ref, useTemplateRef } from 'vue'
import { users } from '../api/endpoints'
import { reloadUser, session } from '../auth/session'
import { notify } from '../lib/feedback'
import { errorText } from '../lib/useAsync'
import UserAvatar from './UserAvatar.vue'

// Ma photo de profil, cliquable pour la changer. Le backend la recadre en carré : pas besoin
// de la préparer. Après l'envoi, le profil est rechargé : la nouvelle photo apparaît partout
// (en-tête, commentaires...) puisque son adresse change.

const ACCEPTED = 'image/jpeg,image/png,image/gif,image/bmp'   // formats que le backend sait lire
const MAX_SIZE = 10 * 1024 * 1024                              // comme ImageService.MAX_FILE_SIZE

const input = useTemplateRef<HTMLInputElement>('input')
const busy = ref(false)

async function run(action: () => Promise<unknown>, done: string) {
  busy.value = true
  try {
    await action()
    await reloadUser()
    notify(done)
  } catch (e) {
    notify(errorText(e, 'Action impossible.'), 'error')
  } finally {
    busy.value = false
  }
}

function choose(file: File | undefined) {
  if (input.value) input.value.value = ''   // permet de reprendre le même fichier ensuite
  if (!file) return
  if (file.size > MAX_SIZE) {
    notify('L\'image dépasse 10 Mo.', 'error')
    return
  }
  void run(() => users.uploadAvatar(file), 'Photo de profil mise à jour.')
}
</script>

<template>
  <div v-if="session.user" class="avatar-editor">
    <button type="button" class="avatar-button" :disabled="busy" title="Changer ma photo de profil"
            aria-label="Changer ma photo de profil" @click="input?.click()">
      <UserAvatar :user="session.user" />
      <span class="avatar-overlay" aria-hidden="true">
        <v-progress-circular v-if="busy" indeterminate size="24" />
        <v-icon v-else icon="fas fa-camera" />
      </span>
    </button>
    <input ref="input" type="file" :accept="ACCEPTED" hidden
           @change="choose(($event.target as HTMLInputElement).files?.[0])">
    <v-btn v-if="session.user.avatarUrl" variant="text" size="x-small" :disabled="busy"
           @click="run(() => users.deleteAvatar(), 'Photo de profil retirée.')">
      Retirer la photo
    </v-btn>
  </div>
</template>
