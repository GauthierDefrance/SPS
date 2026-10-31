// Retours à l'utilisateur partagés par toute l'application, affichés par App.vue :
//  - notify()  : petit message en bas de l'écran ("Modifications enregistrées.")
//  - confirm() : fenêtre de confirmation avant une action irréversible (remplace window.confirm)

import { reactive, ref } from 'vue'

export interface Notice {
  text: string
  color?: 'success' | 'error' | 'info'
}

/** File des messages : <v-snackbar-queue> les affiche l'un après l'autre. */
export const notices = ref<Notice[]>([])

export function notify(text: string, color: Notice['color'] = 'success') {
  notices.value.push({ text, color })
}

export interface ConfirmOptions {
  title: string
  text?: string
  confirmText?: string
  danger?: boolean        // bouton rouge : suppression...
}

export const confirmState = reactive({
  open: false,
  options: { title: '' } as ConfirmOptions,
  answer: (() => {}) as (ok: boolean) => void,
})

/** Ouvre la fenêtre de confirmation ; résolue à true si l'utilisateur confirme. */
export function confirm(options: ConfirmOptions): Promise<boolean> {
  confirmState.answer(false)   // une fenêtre déjà ouverte est annulée
  return new Promise((resolve) => {
    confirmState.options = options
    confirmState.answer = (ok) => {
      confirmState.open = false
      confirmState.answer = () => {}
      resolve(ok)
    }
    confirmState.open = true
  })
}
