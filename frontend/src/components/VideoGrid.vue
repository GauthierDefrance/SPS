<script setup lang="ts">
import type { VideoListItem } from '../api/types'
import { formatAgo, formatViews } from '../lib/format'
import VideoThumbnail from './VideoThumbnail.vue'

// Grille de cartes de vidéos (recherche, chaîne, abonnements, j'aime).
defineProps<{ videos: VideoListItem[]; empty: string }>()
</script>

<template>
  <p v-if="!videos.length" class="muted">{{ empty }}</p>
  <div v-else class="grid">
    <article v-for="video in videos" :key="video.publicId" class="card">
      <router-link :to="`/watch/${video.publicId}`" class="card-link">
        <VideoThumbnail :video-id="video.publicId" :duration="video.duration" :title="video.title" />
        <h3 class="card-title">{{ video.title }}</h3>
      </router-link>
      <router-link v-if="video.ownerId" :to="`/channel/${video.ownerId}`" class="card-channel">
        {{ video.ownerName ?? 'Chaîne' }}
      </router-link>
      <p class="card-meta">{{ formatViews(video.views) }} · {{ formatAgo(video.publishedAt) }}</p>
    </article>
  </div>
</template>
