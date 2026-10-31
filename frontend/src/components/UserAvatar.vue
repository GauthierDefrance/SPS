<script setup lang="ts">
import { computed, ref } from 'vue'
import { fullName } from '../lib/format'

// Photo de profil, ou l'initiale du nom sur fond de couleur si l'utilisateur n'en a pas
// (ou si l'image ne se charge pas).

const props = withDefaults(defineProps<{
  user: { name: string | null; surname: string | null; avatarUrl?: string | null }
  size?: 'tiny' | 'small' | 'medium'
}>(), { size: 'medium' })

const PIXELS = { tiny: 26, small: 40, medium: 72 }

// Mémorise l'adresse qui a échoué : une nouvelle photo (nouvelle adresse) est retentée
const failedUrl = ref<string | null>(null)
const image = computed(() => (props.user.avatarUrl && props.user.avatarUrl !== failedUrl.value ? props.user.avatarUrl : null))
const initial = computed(() => fullName(props.user).charAt(0).toUpperCase())
</script>

<template>
  <v-avatar :size="PIXELS[size]" color="primary" :class="'avatar-' + size">
    <v-img v-if="image" :src="image" alt="" cover @error="failedUrl = image" />
    <span v-else aria-hidden="true">{{ initial }}</span>
  </v-avatar>
</template>
