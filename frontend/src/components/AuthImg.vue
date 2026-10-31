<script setup lang="ts">
import { useAuthImage } from '../lib/useAuthImage'

// <img> pour une image réservée à l'utilisateur connecté (miniatures d'un brouillon...) :
// l'image est téléchargée avec le token (voir useAuthImage).
const props = defineProps<{ src: string; alt?: string }>()
const image = useAuthImage(() => props.src)
</script>

<template>
  <img v-if="image.src.value" :src="image.src.value" :alt="alt ?? ''">
  <span v-else class="auth-img-placeholder" aria-hidden="true">
    <v-icon v-if="image.failed.value" icon="fas fa-film" />
  </span>
</template>
