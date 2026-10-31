<script setup lang="ts">
import { ref } from 'vue'
import { users } from '../../api/endpoints'
import { useAsync, usePolling } from '../../lib/useAsync'
import AppPager from '../AppPager.vue'
import UploadForm from '../UploadForm.vue'
import OwnedVideoRow from './OwnedVideoRow.vue'

// "Mes vidéos" : toutes mes vidéos (brouillons compris) et l'envoi d'une nouvelle.

const page = ref(0)
const { data: list, error, reload } = useAsync(() => users.myVideos(page.value), page)

// Tant qu'une vidéo est en attente ou en traitement, on rafraîchit la liste toutes les 2 s
usePolling(() => list.value?.items.some((v) => v.status === 'PENDING' || v.status === 'PROCESSING') ?? false, reload)
</script>

<template>
  <div class="with-sidebar reverse">
    <div>
      <v-alert v-if="error" type="error" :text="error" />
      <p v-if="list?.items.length === 0" class="muted">Tu n'as pas encore publié de vidéo.</p>
      <ul class="owned-list">
        <OwnedVideoRow v-for="video in list?.items" :key="video.publicId" :video="video" @changed="reload" />
      </ul>
      <AppPager v-if="list" :page="list.page" :total-pages="list.totalPages" @change="page = $event" />
    </div>
    <aside class="sidebar">
      <UploadForm />
    </aside>
  </div>
</template>
