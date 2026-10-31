import { ref, watch } from 'vue'
import { apiFetch } from '../api/client'

/**
 * Image réservée à son propriétaire (miniature d'un brouillon ou d'une vidéo privée).
 * Une balise <img src> n'envoie pas le token de connexion : le serveur répondrait 404.
 * On télécharge donc l'image avec fetch (token compris), puis on l'affiche depuis la mémoire
 * du navigateur (adresse "blob:"). Le cache HTTP du navigateur (ETag) fonctionne comme pour <img>.
 *
 * url() null : pas d'image. src reste vide tant que l'image n'est pas chargée ; failed passe à
 * true si elle n'existe pas.
 */
export function useAuthImage(url: () => string | null) {
  const src = ref<string>()
  const failed = ref(false)

  watch(url, async (address, _, onCleanup) => {
    let objectUrl: string | undefined
    let cancelled = false
    onCleanup(() => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)   // libère la mémoire de l'image précédente
    })
    src.value = undefined
    failed.value = false
    if (!address) return
    try {
      const blob = await (await apiFetch(address)).blob()
      if (cancelled) return
      objectUrl = URL.createObjectURL(blob)
      src.value = objectUrl
    } catch {
      if (!cancelled) failed.value = true
    }
  }, { immediate: true })

  return { src, failed }
}
