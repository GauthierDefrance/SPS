// Vuetify : la bibliothèque de composants (boutons, champs, onglets, fenêtres...).
// Les composants sont importés automatiquement là où un gabarit les utilise (vite-plugin-vuetify) :
// pas d'import à écrire, et seuls ceux qui servent sont inclus dans le site.

import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'
import type { App } from 'vue'
import { createVuetify } from 'vuetify'
import { aliases, fa } from 'vuetify/iconsets/fa-svg'
import { fr } from 'vuetify/locale'
import 'vuetify/styles'
import './icons'

const vuetify = createVuetify({
  // Textes des composants (pagination, "Aucune donnée"...) en français
  locale: { locale: 'fr', messages: { fr } },
  icons: {
    defaultSet: 'fa',
    aliases: { ...aliases, warning: 'fas fa-circle-exclamation' },
    sets: { fa },
  },
  theme: {
    // Thème clair ou sombre selon le réglage du système, comme le reste du site (index.css)
    defaultTheme: 'system',
    themes: {
      light: { colors: { primary: '#d62839', secondary: '#16161a' } },
      dark: { colors: { primary: '#ef4458', secondary: '#ececf1', background: '#0f0f12', surface: '#1c1c21' } },
    },
  },
  // Réglages par défaut de chaque composant, pour ne pas les répéter dans chaque page
  defaults: {
    VBtn: { variant: 'flat', rounded: 'pill', class: 'text-none' },
    VTextField: { variant: 'outlined', density: 'comfortable', hideDetails: 'auto' },
    VTextarea: { variant: 'outlined', density: 'comfortable', hideDetails: 'auto' },
    VSelect: { variant: 'outlined', density: 'comfortable', hideDetails: true },
    VAlert: { variant: 'tonal', density: 'compact' },
    VChip: { size: 'small', variant: 'tonal' },
  },
})

export function installVuetify(app: App) {
  app.component('FontAwesomeIcon', FontAwesomeIcon)   // utilisé par le jeu d'icônes "fa-svg" de Vuetify
  app.use(vuetify)
}
