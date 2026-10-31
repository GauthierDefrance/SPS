import vue from '@vitejs/plugin-vue'
import { defineConfig, loadEnv } from 'vite'
import vuetify from 'vite-plugin-vuetify'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    // vuetify : importe chaque composant Vuetify utilisé dans un gabarit (<v-btn>...), et seulement ceux-là
    plugins: [vue(), vuetify({ autoImport: true })],
    // Vuetify n'est pas pré-optimisé par Vite : sinon, chaque page qui utilise un composant Vuetify
    // pas encore vu relancerait l'optimisation en cours de navigation (erreur "Outdated Optimize Dep")
    optimizeDeps: { exclude: ['vuetify'] },
    server: {
      // En développement, Vite relaie /api vers le backend Spring : pour le navigateur, page et API
      // ont la même origine (localhost:5173). Pas de CORS à configurer, et le cookie de session
      // (refresh token, SameSite=Strict) est bien envoyé.
      proxy: {
        '/api': env.BACKEND_URL ?? 'http://localhost:8081',
      },
    },
  }
})
