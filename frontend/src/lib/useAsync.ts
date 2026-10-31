import { ref, shallowRef, watch, type WatchSource } from 'vue'

/** Message lisible d'une erreur (celles de l'API ont déjà un message en français). */
export function errorText(error: unknown, fallback = 'Une erreur est survenue.'): string {
  return error instanceof Error && error.message ? error.message : fallback
}

/**
 * Charge une donnée asynchrone (appel d'API), et la recharge quand `source` change (ex. l'id
 * de la vidéo dans l'URL). Pendant un rechargement, l'ancienne donnée reste affichée (pas de
 * clignotement) et `loading` vaut true. Une réponse arrivée trop tard (une autre requête est
 * partie entre-temps) est ignorée.
 */
export function useAsync<T>(load: () => Promise<T>, source: WatchSource = () => null) {
  const data = shallowRef<T>()
  const error = ref<string>()
  const loading = ref(false)
  let latest = 0

  async function reload() {
    const call = ++latest
    loading.value = true
    error.value = undefined
    try {
      const result = await load()
      if (call === latest) data.value = result
    } catch (e) {
      if (call === latest) error.value = errorText(e)
    } finally {
      if (call === latest) loading.value = false
    }
  }

  /** Modifie la donnée localement (ex. après un like) sans recharger. */
  function update(change: (current: T) => T) {
    if (data.value !== undefined) data.value = change(data.value)
  }

  watch(source, reload, { immediate: true })
  return { data, error, loading, reload, update }
}

/** Appelle `action` toutes les `ms` millisecondes tant que `active()` est vrai (suivi d'un traitement). */
export function usePolling(active: () => boolean, action: () => void, ms = 2000) {
  watch(active, (isActive, _, onCleanup) => {
    if (!isActive) return
    const timer = setInterval(action, ms)
    onCleanup(() => clearInterval(timer))
  }, { immediate: true })
}
