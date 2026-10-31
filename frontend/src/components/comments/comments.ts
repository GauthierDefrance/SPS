import type { Comment } from '../../api/types'

export const MAX_LENGTH = 2000   // comme Comment.MAX_LENGTH côté backend

/** Ajoute une page à une liste déjà chargée, sans doublon (un ajout entre-temps décale les pages). */
export function appendPage(items: Comment[], page: Comment[]): Comment[] {
  return [...items, ...page.filter((c) => !items.some((known) => known.id === c.id))]
}
