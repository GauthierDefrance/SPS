<script setup lang="ts">
import { computed, ref } from 'vue'
import { videos } from '../api/endpoints'
import type { Video, VideoPolicy } from '../api/types'
import { errorText } from '../lib/useAsync'
import ThumbnailPicker from './ThumbnailPicker.vue'

// Les informations d'une vidéo, modifiées par son propriétaire : titre, description, visibilité
// et miniature. La miniature s'enregistre dès qu'on la choisit (thumbnailChange) ; le reste avec
// le bouton "Enregistrer". Pour un brouillon, "Publier" enregistre puis publie la vidéo : possible
// seulement une fois traitée, quand les images extraites sont proposées.

const props = defineProps<{ video: Video }>()
const emit = defineEmits<{
  saved: [video: Video]
  published: [video: Video]
  cancel: []
  thumbnailChange: [video: Video]
}>()

const POLICIES: { title: string; value: VideoPolicy; props: { prependIcon: string; subtitle: string } }[] = [
  { title: 'Publique', value: 'PUBLIC', props: { prependIcon: 'fas fa-compass', subtitle: 'Dans le catalogue et la recherche' } },
  { title: 'Non répertoriée', value: 'UNLISTED', props: { prependIcon: 'fas fa-share-from-square', subtitle: 'Visible avec le lien uniquement' } },
  { title: 'Privée', value: 'PRIVATE', props: { prependIcon: 'fas fa-eye-slash', subtitle: 'Visible par moi seul' } },
]

// Valeurs de départ seulement : le suivi du traitement met à jour `video` sans effacer la saisie
const title = ref(props.video.title)
const description = ref(props.video.description ?? '')
const policy = ref<VideoPolicy>(props.video.policy)
const saving = ref(false)
const error = ref<string>()
const draft = computed(() => !props.video.publishedAt)
const ready = computed(() => props.video.status === 'READY')

async function save(publish: boolean) {
  saving.value = true
  error.value = undefined
  try {
    // Description vide : le backend l'efface
    const saved = await videos.update(props.video.id,
      { title: title.value.trim(), description: description.value.trim(), policy: policy.value })
    if (publish) emit('published', await videos.publish(props.video.id))
    else emit('saved', saved)
  } catch (e) {
    error.value = errorText(e, 'Enregistrement impossible.')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <v-card variant="outlined">
    <v-form class="edit-video" @submit.prevent="save(false)">
      <v-text-field v-model="title" label="Titre" required maxlength="255" counter :disabled="saving"
                    :rules="[(v: string) => !!v.trim() || 'Le titre est obligatoire.']" />
      <v-textarea v-model="description" label="Description" rows="5" auto-grow maxlength="5000" counter
                  placeholder="Présente ta vidéo aux spectateurs" :disabled="saving" />
      <v-select v-model="policy" label="Visibilité" :items="POLICIES" :disabled="saving" />
      <ThumbnailPicker :video="video" @change="emit('thumbnailChange', $event)" />

      <v-alert v-if="error" type="error" :text="error" />
      <v-alert v-if="draft && !ready" :type="video.status === 'FAILED' ? 'error' : 'info'"
               :text="video.status === 'FAILED'
                 ? 'Le traitement a échoué : la vidéo ne peut pas être publiée.'
                 : 'Tu pourras publier la vidéo à la fin de son traitement, après avoir choisi sa miniature.'" />

      <div class="form-actions">
        <v-btn variant="text" :disabled="saving" @click="emit('cancel')">Retour</v-btn>
        <v-btn type="submit" :variant="draft ? 'tonal' : 'flat'" :color="draft ? undefined : 'primary'"
               :loading="saving" :disabled="!title.trim()">
          {{ draft ? 'Enregistrer le brouillon' : 'Enregistrer' }}
        </v-btn>
        <v-btn v-if="draft" color="primary" prepend-icon="fas fa-cloud-arrow-up" :loading="saving"
               :disabled="!ready || !title.trim()" @click="save(true)">
          Publier
        </v-btn>
      </div>
    </v-form>
  </v-card>
</template>
