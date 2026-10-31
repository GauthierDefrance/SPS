<script setup lang="ts">
import { ref } from 'vue'
import { users } from '../../api/endpoints'
import { useAsync } from '../../lib/useAsync'
import AppPager from '../AppPager.vue'
import VideoGrid from '../VideoGrid.vue'

// Les vidéos que j'aime, les plus récemment aimées d'abord.

const page = ref(0)
const { data: list, error } = useAsync(() => users.likes(page.value), page)
</script>

<template>
  <v-alert v-if="error" type="error" :text="error" />
  <template v-else-if="list">
    <VideoGrid :videos="list.items" empty="Tu n'as encore aimé aucune vidéo." />
    <AppPager :page="list.page" :total-pages="list.totalPages" @change="page = $event" />
  </template>
  <v-progress-linear v-else indeterminate color="primary" />
</template>
