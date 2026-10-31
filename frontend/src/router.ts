import { createRouter, createWebHistory } from 'vue-router'
import { session, sessionReady } from './auth/session'
import { safeNext } from './lib/safeNext'

// meta.requiresAuth : page réservée aux connectés (sinon, connexion puis retour ici)
// meta.guestOnly    : connexion / inscription, inutiles une fois connecté
declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    guestOnly?: boolean
  }
}

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: () => import('./pages/SearchPage.vue') },
    { path: '/login', component: () => import('./pages/LoginPage.vue'), meta: { guestOnly: true } },
    { path: '/register', component: () => import('./pages/RegisterPage.vue'), meta: { guestOnly: true } },
    { path: '/watch/:id', component: () => import('./pages/WatchPage.vue'), props: true },
    { path: '/channel/:id', component: () => import('./pages/ChannelPage.vue'), props: true },
    { path: '/subscriptions', component: () => import('./pages/SubscriptionsPage.vue'), meta: { requiresAuth: true } },
    { path: '/me', component: () => import('./pages/ProfilePage.vue'), meta: { requiresAuth: true } },
    { path: '/me/videos/:id', component: () => import('./pages/VideoDetailsPage.vue'), props: true, meta: { requiresAuth: true } },
    { path: '/:pathMatch(.*)*', component: () => import('./pages/NotFoundPage.vue') },
  ],
  // Nouvelle page : on remonte en haut ; "retour" du navigateur : on retrouve la position
  scrollBehavior: (_to, _from, saved) => saved ?? { top: 0 },
})

// Le code d'une page n'a pas pu être chargé (nouvelle version du site déployée entre-temps,
// réseau coupé...) : on charge l'adresse directement, ce qui récupère la version à jour.
router.onError((error: Error, to) => {
  if (/dynamically imported module|Importing a module script failed/.test(error.message)) window.location.assign(to.fullPath)
})

router.beforeEach(async (to) => {
  if (to.meta.requiresAuth || to.meta.guestOnly) await sessionReady
  if (to.meta.requiresAuth && !session.user) return { path: '/login', query: { next: to.fullPath } }
  if (to.meta.guestOnly && session.user) return safeNext(to.query.next as string | null)
})
