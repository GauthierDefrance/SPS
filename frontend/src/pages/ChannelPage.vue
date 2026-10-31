<script setup lang="ts">
import { ref } from 'vue'
import { users } from '../api/endpoints'
import AppPager from '../components/AppPager.vue'
import SubscribeButton from '../components/SubscribeButton.vue'
import UserAvatar from '../components/UserAvatar.vue'
import VideoGrid from '../components/VideoGrid.vue'
import { formatCount, fullName } from '../lib/format'
import { useAsync } from '../lib/useAsync'

// Page publique d'une chaîne : son profil et ses vidéos.

const props = defineProps<{ id: string }>()
const page = ref(0)
const { data: channel, error, update } = useAsync(() => users.profile(props.id), () => props.id)
const { data: list } = useAsync(() => users.channelVideos(props.id, page.value), () => [props.id, page.value])

function subscribed(value: boolean) {
  update((c) => ({ ...c, subscribed: value, subscribers: c.subscribers + (value ? 1 : -1) }))
}
</script>

<template>
  <v-alert v-if="error" type="error" :text="error" />
  <v-progress-linear v-else-if="!channel" indeterminate color="primary" />
  <section v-else>
    <div class="channel-header">
      <UserAvatar :user="channel" />
      <div>
        <h1>{{ fullName(channel) }}</h1>
        <p class="muted">
          {{ formatCount(channel.subscribers) }} abonné{{ channel.subscribers > 1 ? 's' : '' }} ·
          {{ channel.videos }} vidéo{{ channel.videos > 1 ? 's' : '' }}
        </p>
      </div>
      <SubscribeButton :channel-id="channel.id" :subscribed="channel.subscribed" class="ms-auto" @change="subscribed" />
    </div>

    <template v-if="list">
      <VideoGrid :videos="list.items" empty="Cette chaîne n'a pas encore publié de vidéo." />
      <AppPager :page="list.page" :total-pages="list.totalPages" @change="page = $event" />
    </template>
  </section>
</template>
