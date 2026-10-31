/** Où revenir après connexion : seulement un chemin interne (jamais une URL d'un autre site). */
export function safeNext(next: string | null): string {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/'
}
