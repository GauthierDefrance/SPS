<template>
  <v-empty-state icon="fas fa-film" title="Page introuvable" text="Cette adresse ne mène à aucune page.">
    <template #actions>
      <v-btn to="/" color="primary" prepend-icon="fas fa-compass">Retour à l'accueil</v-btn>
    </template>
  </v-empty-state>
</template>
