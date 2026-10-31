<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { videos } from '../api/endpoints'
import type { DurationFilter, SortOrder, UploadedFilter } from '../api/types'
import AppPager from '../components/AppPager.vue'
import VideoGrid from '../components/VideoGrid.vue'
import { useAsync } from '../lib/useAsync'

// Recherche et exploration. Tous les critères sont dans l'URL (?q=...&duration=...) :
// une recherche se partage, et le bouton "retour" du navigateur fonctionne.

const DURATIONS: { title: string; value: DurationFilter }[] = [
  { title: 'Toutes durées', value: 'ANY' }, { title: 'Moins de 4 min', value: 'SHORT' },
  { title: '4 à 20 min', value: 'MEDIUM' }, { title: 'Plus de 20 min', value: 'LONG' },
]
const UPLOADED: { title: string; value: UploadedFilter }[] = [
  { title: 'Toutes dates', value: 'ANY' }, { title: 'Aujourd\'hui', value: 'DAY' }, { title: 'Cette semaine', value: 'WEEK' },
  { title: 'Ce mois-ci', value: 'MONTH' }, { title: 'Cette année', value: 'YEAR' },
]
const SORTS: { title: string; value: SortOrder }[] = [
  { title: 'Plus récentes', value: 'RECENT' }, { title: 'Plus anciennes', value: 'OLDEST' },
  { title: 'Plus vues', value: 'VIEWS' }, { title: 'Plus aimées', value: 'LIKES' },
]

const route = useRoute()
const router = useRouter()
const param = (name: string, fallback: string) => (typeof route.query[name] === 'string' ? route.query[name] : fallback)

const criteria = computed(() => ({
  q: param('q', ''),
  duration: param('duration', 'ANY') as DurationFilter,
  uploaded: param('uploaded', 'ANY') as UploadedFilter,
  sort: param('sort', 'RECENT') as SortOrder,
  page: Number(param('page', '0')),
}))

const { data, error, loading } = useAsync(() => videos.search(criteria.value), criteria)

/** Change un critère ; revient à la première page (sauf si c'est la page qui change). */
function set(name: string, value: string, defaultValue: string) {
  const query = { ...route.query }
  if (value === defaultValue) delete query[name]
  else query[name] = value
  if (name !== 'page') delete query.page
  void router.push({ query })
}

const filtered = computed(() => criteria.value.q || criteria.value.duration !== 'ANY' || criteria.value.uploaded !== 'ANY')
</script>

<template>
  <section>
    <div class="page-header">
      <h1>{{ criteria.q ? `Résultats pour « ${criteria.q} »` : 'Explorer' }}</h1>
      <span v-if="data" class="muted">
        {{ data.totalItems }} vidéo{{ data.totalItems > 1 ? 's' : '' }}
      </span>
    </div>

    <div class="filters" aria-label="Filtres">
      <v-select label="Durée" :items="DURATIONS" :model-value="criteria.duration"
                @update:model-value="set('duration', $event, 'ANY')" />
      <v-select label="Date de mise en ligne" :items="UPLOADED" :model-value="criteria.uploaded"
                @update:model-value="set('uploaded', $event, 'ANY')" />
      <v-select label="Trier par" :items="SORTS" :model-value="criteria.sort"
                @update:model-value="set('sort', $event, 'RECENT')" />
      <v-btn v-if="criteria.q" variant="text" prepend-icon="fas fa-xmark" @click="set('q', '', '')">
        Effacer la recherche
      </v-btn>
    </div>

    <v-alert v-if="error" type="error" :text="error" class="mb-4" />
    <div v-if="data" :class="{ loading: loading }">
      <VideoGrid :videos="data.items"
                 :empty="filtered ? 'Aucune vidéo ne correspond à ces critères.' : 'Aucune vidéo pour l\'instant.'" />
      <AppPager :page="data.page" :total-pages="data.totalPages"
                @change="set('page', String($event), '0')" />
    </div>
    <v-progress-linear v-else-if="!error" indeterminate color="primary" />
  </section>
</template>
