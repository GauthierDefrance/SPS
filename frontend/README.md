# Frontend SPS

Interface web de SPS : **Vue 3** (TypeScript), **Vue Router**, composants **Vuetify** et icônes
**Font Awesome** (version gratuite). Tous sont open source (licence MIT ; icônes sous CC BY 4.0).

```bash
npm install
npm run dev        # http://localhost:5173 (le backend doit tourner sur le port 8081)
npm run typecheck  # vérification des types, gabarits .vue compris (vue-tsc)
npm run lint       # ESLint
npm run build      # vérification des types puis version de production dans dist/
```

## Organisation

```
src/
  main.ts              démarrage : Vuetify, routeur, styles
  App.vue              squelette commun : en-tête, page, messages, fenêtre de confirmation
  router.ts            les pages et leurs adresses ; pages réservées aux connectés
  plugins/
    vuetify.ts         réglages de Vuetify : thème (clair / sombre), français, valeurs par défaut
    icons.ts           les icônes Font Awesome utilisées (à compléter pour en ajouter une)
  api/                 appels à l'API du backend (client.ts gère le token), types des réponses
  auth/session.ts      l'utilisateur connecté, connexion / déconnexion
  lib/                 outils : chargement de données (useAsync), images privées, messages, formats
  pages/               une page par adresse (SearchPage, WatchPage, ProfilePage...)
  components/          les briques réutilisées par les pages (lecteur, commentaires, miniatures...)
  index.css, App.css   couleurs et mise en page propres à l'application
```

## Un composant Vue

Un fichier `.vue` réunit le code, le gabarit HTML et, si besoin, le style :

```vue
<script setup lang="ts">
import { ref } from 'vue'
const titre = ref('')          // donnée "réactive" : l'affichage suit ses changements
</script>

<template>
  <v-text-field v-model="titre" label="Titre" />   <!-- v-model : liaison dans les deux sens -->
  <v-btn color="primary" prepend-icon="fas fa-play" :disabled="!titre">Lire</v-btn>
</template>
```

- Les composants `v-…` viennent de Vuetify : ils sont importés automatiquement
  (`vite-plugin-vuetify`). La liste et leurs options : https://vuetifyjs.com/en/components/all/
- Les icônes s'écrivent `fas fa-nom` (style plein) ou `far fa-nom` (contour), avec les noms de
  https://fontawesome.com/search?ic=free. Une nouvelle icône doit être ajoutée dans
  [src/plugins/icons.ts](src/plugins/icons.ts) : seules les icônes listées sont incluses dans le site.
- Charger des données de l'API : `useAsync` ([src/lib/useAsync.ts](src/lib/useAsync.ts)).

  ```ts
  const { data: video, error } = useAsync(() => videos.get(props.id), () => props.id)
  ```

## Versions

- **TypeScript 6.0** et non 7 : `vue-tsc` (qui vérifie les fichiers `.vue`) a besoin de l'API
  JavaScript de TypeScript, absente du nouveau compilateur natif de TypeScript 7.
- **Vuetify 4.2** : version stable, publiée depuis plus de deux semaines au moment du choix.
