<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login } from '../auth/session'
import { safeNext } from '../lib/safeNext'
import { errorText } from '../lib/useAsync'

// Connexion, puis retour à la page demandée (?next=...).

const route = useRoute()
const router = useRouter()
const next = computed(() => safeNext(route.query.next as string | null))
const email = ref('')
const password = ref('')
const showPassword = ref(false)
const busy = ref(false)
const error = ref<string>()

async function submit() {
  busy.value = true
  error.value = undefined
  try {
    await login(email.value.trim(), password.value)
    void router.replace(next.value)
  } catch (e) {
    error.value = errorText(e, 'Connexion impossible.')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <v-card class="auth-card" variant="outlined">
    <v-card-title class="text-h5">Connexion</v-card-title>
    <v-card-text>
      <v-form class="form" @submit.prevent="submit">
        <v-text-field v-model="email" label="Email" type="email" autocomplete="email" required prepend-inner-icon="fas fa-user" />
        <v-text-field v-model="password" label="Mot de passe" :type="showPassword ? 'text' : 'password'"
                      autocomplete="current-password" required
                      :append-inner-icon="showPassword ? 'fas fa-eye-slash' : 'fas fa-eye'"
                      @click:append-inner="showPassword = !showPassword" />
        <v-alert v-if="error" type="error" :text="error" />
        <v-btn type="submit" color="primary" size="large" block :loading="busy">Se connecter</v-btn>
      </v-form>
      <p class="muted mt-4">
        Pas encore de compte ? <router-link :to="{ path: '/register', query: { next } }" class="strong">Créer un compte</router-link>
      </p>
    </v-card-text>
  </v-card>
</template>
