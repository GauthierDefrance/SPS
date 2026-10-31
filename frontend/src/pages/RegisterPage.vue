<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { register } from '../auth/session'
import { safeNext } from '../lib/safeNext'
import { errorText } from '../lib/useAsync'

// Inscription, puis connexion automatique et retour à la page demandée (?next=...).

const route = useRoute()
const router = useRouter()
const next = computed(() => safeNext(route.query.next as string | null))
const form = reactive({ email: '', password: '', confirm: '', name: '', surname: '' })
const busy = ref(false)
const error = ref<string>()

// Mêmes règles que le backend (RegisterRequest), vérifiées ici pour un message immédiat
const rules = {
  required: (v: string) => !!v.trim() || 'Obligatoire.',
  password: (v: string) => v.length >= 8 || 'Au moins 8 caractères.',
  same: (v: string) => v === form.password || 'Les deux mots de passe ne correspondent pas.',
}

async function submit() {
  if (form.password.length < 8 || form.password !== form.confirm) return
  busy.value = true
  error.value = undefined
  try {
    await register({ email: form.email.trim(), password: form.password, name: form.name.trim(),
      surname: form.surname.trim() || undefined })
    void router.replace(next.value)
  } catch (e) {
    error.value = errorText(e, 'Inscription impossible.')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <v-card class="auth-card" variant="outlined">
    <v-card-title class="text-h5">Créer un compte</v-card-title>
    <v-card-text>
      <v-form class="form" @submit.prevent="submit">
        <div class="row">
          <v-text-field v-model="form.name" label="Prénom" autocomplete="given-name" required maxlength="100"
                        :rules="[rules.required]" />
          <v-text-field v-model="form.surname" label="Nom (optionnel)" autocomplete="family-name" maxlength="100" />
        </div>
        <v-text-field v-model="form.email" label="Email" type="email" autocomplete="email" required maxlength="255"
                      :rules="[rules.required]" />
        <v-text-field v-model="form.password" label="Mot de passe" type="password" autocomplete="new-password" required
                      maxlength="72" hint="8 caractères minimum" persistent-hint :rules="[rules.password]" />
        <v-text-field v-model="form.confirm" label="Confirmer le mot de passe" type="password" autocomplete="new-password"
                      required :rules="[rules.same]" />
        <v-alert v-if="error" type="error" :text="error" />
        <v-btn type="submit" color="primary" size="large" block :loading="busy">Créer mon compte</v-btn>
      </v-form>
      <p class="muted mt-4">
        Déjà inscrit ? <router-link :to="{ path: '/login', query: { next } }" class="strong">Se connecter</router-link>
      </p>
    </v-card-text>
  </v-card>
</template>
