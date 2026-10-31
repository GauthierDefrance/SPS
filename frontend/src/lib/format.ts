// Mise en forme pour l'affichage (durées, compteurs, dates relatives).

/** 75 -> "1:15", 3725 -> "1:02:05" */
export function formatDuration(seconds: number): string {
  const total = Math.max(0, Math.round(seconds))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = String(total % 60).padStart(2, '0')
  return h > 0 ? `${h}:${String(m).padStart(2, '0')}:${s}` : `${m}:${s}`
}

const compact = new Intl.NumberFormat('fr-FR', { notation: 'compact', maximumFractionDigits: 1 })

/** 1234 -> "1,2 k vues" */
export function formatViews(views: number): string {
  return compact.format(views) + (views > 1 ? ' vues' : ' vue')
}

export function formatCount(n: number): string {
  return compact.format(n)
}

const relative = new Intl.RelativeTimeFormat('fr-FR', { numeric: 'auto' })
const STEPS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 365 * 24 * 3600], ['month', 30 * 24 * 3600], ['week', 7 * 24 * 3600],
  ['day', 24 * 3600], ['hour', 3600], ['minute', 60],
]

/** Date ISO -> "il y a 3 jours" */
export function formatAgo(iso: string): string {
  const seconds = (Date.now() - new Date(iso).getTime()) / 1000
  for (const [unit, size] of STEPS) {
    if (seconds >= size) return relative.format(-Math.floor(seconds / size), unit)
  }
  return 'à l\'instant'
}

export function fullName(user: { name: string | null; surname: string | null }): string {
  return [user.name, user.surname].filter(Boolean).join(' ') || 'Utilisateur'
}
