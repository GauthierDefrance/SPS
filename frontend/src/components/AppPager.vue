<script setup lang="ts">
// Navigation entre les pages de résultats. Masquée s'il n'y a qu'une page.
// Les pages de l'API commencent à 0, celles affichées à 1.
defineProps<{ page: number; totalPages: number }>()
const emit = defineEmits<{ change: [page: number] }>()
</script>

<template>
  <v-pagination v-if="totalPages > 1" class="pager" :model-value="page + 1" :length="totalPages" :total-visible="7"
                rounded="circle" density="comfortable" @update:model-value="emit('change', $event - 1)" />
</template>
