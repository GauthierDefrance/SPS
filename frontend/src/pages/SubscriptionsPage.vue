<script setup lang="ts">
import { ref } from 'vue'
import { users } from '../api/endpoints'
import { reloadUser } from '../auth/session'
import AppPager from '../components/AppPager.vue'
import UserAvatar from '../components/UserAvatar.vue'
import VideoGrid from '../components/VideoGrid.vue'
import { fullName } from '../lib/format'
import { useAsync } from '../lib/useAsync'

// Les dernières vidéos des chaînes suivies, et la liste de ces chaînes.

const page = ref(0)
const feed = useAsync(() => users.feed(page.value), page)
const channels = useAsync(() => users.subscriptions())
const { data: feedPage, error: feedError } = feed
const { data: channelPage } = channels

async function unsubscribe(channelId: string) {
  await users.unsubscribe(channelId)
  void channels.reload()
  void feed.reload()
  void reloadUser()
}
</script>

<template>
  <div class="with-sidebar">
    <section>
      <h1>Abonnements</h1>
      <v-alert v-if="feedError" type="error" :text="feedError" />
      <template v-if="feedPage">
        <VideoGrid :videos="feedPage.items"
                   empty="Aucune vidéo : abonne-toi à des chaînes depuis leurs vidéos pour les retrouver ici." />
        <AppPager :page="feedPage.page" :total-pages="feedPage.totalPages" @change="page = $event" />
      </template>
      <v-progress-linear v-else-if="!feedError" indeterminate color="primary" />
    </section>

    <v-card class="sidebar" variant="outlined">
      <v-card-title>
        Chaînes suivies <span v-if="channelPage" class="muted">({{ channelPage.totalItems }})</span>
      </v-card-title>
      <v-card-text v-if="channelPage?.items.length === 0" class="muted">Aucun abonnement pour l'instant.</v-card-text>
      <v-list density="compact">
        <v-list-item v-for="channel in channelPage?.items" :key="channel.id" :to="`/channel/${channel.id}`"
                     :title="fullName(channel)">
          <template #prepend><UserAvatar :user="channel" size="tiny" class="me-3" /></template>
          <template #append>
            <v-btn icon="fas fa-xmark" variant="text" size="small" title="Se désabonner" aria-label="Se désabonner"
                   @click.prevent="unsubscribe(channel.id)" />
          </template>
        </v-list-item>
      </v-list>
    </v-card>
  </div>
</template>
