<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { logout, session } from './auth/session'
import UserAvatar from './components/UserAvatar.vue'
import { confirmState, notices } from './lib/feedback'

// Squelette commun : en-tête (logo, recherche, navigation, compte), la page, et les retours
// partagés (messages en bas de l'écran, fenêtre de confirmation).

const route = useRoute()
const router = useRouter()
const query = ref('')

// La barre de recherche reflète la recherche en cours (?q=...), même après "retour"
watch(() => route.query.q, (q) => { query.value = typeof q === 'string' ? q : '' }, { immediate: true })

function search() {
  const q = query.value.trim()
  void router.push(q ? { path: '/', query: { q } } : '/')
}

async function signOut() {
  await logout()
  void router.push('/')
}
</script>

<template>
  <v-app>
    <v-app-bar flat border="b" density="comfortable" class="topbar">
      <router-link to="/" class="logo" aria-label="SPS, accueil">
        <v-icon icon="fas fa-circle-play" size="small" /><span class="logo-text">SPS</span>
      </router-link>

      <form class="search" role="search" @submit.prevent="search">
        <v-text-field v-model="query" type="search" placeholder="Rechercher une vidéo" aria-label="Rechercher une vidéo"
                      density="compact" rounded="pill" single-line>
          <template #append-inner>
            <v-btn icon="fas fa-magnifying-glass" type="submit" variant="text" size="small" aria-label="Rechercher" />
          </template>
        </v-text-field>
      </form>

      <nav class="nav">
        <v-btn to="/" exact variant="text" prepend-icon="fas fa-compass" class="nav-explore">
          <span class="nav-label">Explorer</span>
        </v-btn>
        <template v-if="session.user">
          <v-btn to="/subscriptions" variant="text" prepend-icon="fas fa-users">
            <span class="nav-label">Abonnements</span>
          </v-btn>
          <v-btn to="/me" variant="text">
            <template #prepend><UserAvatar :user="session.user" size="tiny" /></template>
            <span class="nav-label">Mon profil</span>
          </v-btn>
          <v-btn icon="fas fa-arrow-right-from-bracket" variant="text" size="small" title="Déconnexion"
                 aria-label="Déconnexion" @click="signOut" />
        </template>
        <template v-else-if="session.ready">
          <v-btn to="/login" variant="text" prepend-icon="fas fa-right-to-bracket">
            <span class="nav-label">Connexion</span>
          </v-btn>
          <v-btn to="/register" color="primary" prepend-icon="fas fa-user-plus">Inscription</v-btn>
        </template>
      </nav>
    </v-app-bar>

    <v-main>
      <div class="page">
        <!-- Rechargement de la page : une page réservée attend que la session soit restaurée -->
        <v-progress-linear v-if="!session.ready" indeterminate color="primary" />
        <router-view />
      </div>
    </v-main>

    <v-snackbar-queue v-model="notices" location="bottom" timeout="3000" />

    <v-dialog :model-value="confirmState.open" max-width="440" @update:model-value="confirmState.answer(false)">
      <v-card :title="confirmState.options.title" :text="confirmState.options.text">
        <v-card-actions>
          <v-spacer />
          <v-btn variant="text" @click="confirmState.answer(false)">Annuler</v-btn>
          <v-btn variant="flat" :color="confirmState.options.danger ? 'error' : 'primary'" @click="confirmState.answer(true)">
            {{ confirmState.options.confirmText ?? 'Confirmer' }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-app>
</template>
