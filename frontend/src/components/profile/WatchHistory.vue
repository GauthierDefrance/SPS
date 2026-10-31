<script setup lang="ts">
import { ref } from 'vue'
import { users } from '../../api/endpoints'
import { confirm, notify } from '../../lib/feedback'
import { formatAgo, formatDuration } from '../../lib/format'
import { useAsync } from '../../lib/useAsync'
import AppPager from '../AppPager.vue'
import VideoThumbnail from '../VideoThumbnail.vue'

// Mon historique de visionnage, avec la position où je me suis arrêté.

const page = ref(0)
const { data: list, error, reload } = useAsync(() => users.history(page.value), page)

async function clear() {
  if (!await confirm({ title: 'Effacer tout l\'historique de visionnage ?', confirmText: 'Effacer', danger: true })) return
  await users.clearHistory()
  notify('Historique effacé.')
  void reload()
}
</script>

<template>
  <v-alert v-if="error" type="error" :text="error" />
  <v-progress-linear v-else-if="!list" indeterminate color="primary" />
  <template v-else>
    <v-btn v-if="list.items.length > 0" variant="text" prepend-icon="far fa-trash-can" class="mb-4" @click="clear">
      Effacer l'historique
    </v-btn>
    <p v-else class="muted">Aucune vidéo regardée pour l'instant.</p>
    <ul class="owned-list">
      <li v-for="item in list.items" :key="item.publicId" class="owned">
        <router-link :to="`/watch/${item.publicId}`" class="owned-thumb">
          <VideoThumbnail :video-id="item.publicId" :duration="item.duration" :title="item.title" />
        </router-link>
        <div class="owned-body">
          <router-link :to="`/watch/${item.publicId}`" class="card-title">{{ item.title }}</router-link>
          <p class="muted">
            Regardée {{ formatAgo(item.watchedAt) }}
            <template v-if="item.positionSeconds > 0"> · arrêtée à {{ formatDuration(item.positionSeconds) }}</template>
          </p>
        </div>
      </li>
    </ul>
    <AppPager :page="list.page" :total-pages="list.totalPages" @change="page = $event" />
  </template>
</template>
