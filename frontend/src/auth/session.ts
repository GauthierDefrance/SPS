// État de connexion partagé par toute l'application : un simple objet "réactif" de Vue.
// Toute page qui lit session.user se met à jour d'elle-même quand on se connecte ou se déconnecte.
//
// Au chargement, on tente de restaurer la session avec le cookie de refresh : l'utilisateur
// reste connecté après un rechargement de la page, sans que le token soit stocké nulle part.

import { reactive } from 'vue'
import { setAccessToken, setSessionExpiredHandler } from '../api/client'
import { auth, users } from '../api/endpoints'
import type { Me } from '../api/types'

export const session = reactive({
  user: null as Me | null,
  /** false tant qu'on ne sait pas encore si une session existe (évite un "flash" de la page de connexion) */
  ready: false,
})

setSessionExpiredHandler(() => { session.user = null })

/** Résolue une fois la session restaurée (ou non) : le routeur l'attend avant d'ouvrir une page réservée. */
export const sessionReady: Promise<void> = auth.refresh()
  .then((ok) => (ok ? users.me() : null))
  .then((me) => { session.user = me })
  .catch(() => { session.user = null })
  .finally(() => { session.ready = true })

export async function login(email: string, password: string) {
  const tokens = await auth.login(email, password)
  setAccessToken(tokens.accessToken)
  session.user = await users.me()
}

export async function register(data: { email: string; password: string; name: string; surname?: string }) {
  await auth.register(data)
  await login(data.email, data.password)
}

export async function logout() {
  try {
    await auth.logout()
  } finally {
    setAccessToken(null)
    session.user = null
  }
}

/** Recharge le profil (compteurs après un upload, un abonnement, une nouvelle photo...). */
export async function reloadUser() {
  session.user = await users.me()
}
