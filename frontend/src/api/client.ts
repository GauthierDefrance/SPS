// Client HTTP de l'API. Gère le token d'accès :
//  - gardé en mémoire uniquement (jamais dans localStorage, lisible par n'importe quel script de la page)
//  - envoyé dans l'en-tête "Authorization: Bearer ..."
//  - renouvelé automatiquement : une réponse 401 déclenche POST /api/auth/refresh (le navigateur
//    envoie tout seul le cookie HttpOnly du refresh token), puis la requête est rejouée une fois.

import type { TokenResponse } from './types'

/** Erreur renvoyée par l'API, avec le message du backend quand il y en a un. */
export class ApiError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

let accessToken: string | null = null
let refreshing: Promise<boolean> | null = null
let onSessionExpired: () => void = () => {}

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
}

/** Appelé quand la session ne peut pas être renouvelée (refresh token expiré ou révoqué). */
export function setSessionExpiredHandler(handler: () => void): void {
  onSessionExpired = handler
}

/**
 * Demande un nouveau token d'accès avec le cookie de refresh. Plusieurs requêtes qui reçoivent
 * un 401 en même temps partagent le même appel (un refresh token ne sert qu'une fois).
 */
export function refreshSession(): Promise<boolean> {
  refreshing ??= fetch('/api/auth/refresh', { method: 'POST' })
    .then(async (response) => {
      if (!response.ok) {
        accessToken = null
        return false
      }
      accessToken = ((await response.json()) as TokenResponse).accessToken
      return true
    })
    .catch(() => false)
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

async function errorFrom(response: Response): Promise<ApiError> {
  let message = defaultMessage(response.status)
  try {
    const body = await response.json()
    if (typeof body?.message === 'string' && body.message && !body.message.startsWith('Validation failed')) {
      message = body.message
    }
  } catch {
    // corps vide ou non JSON : on garde le message par défaut
  }
  return new ApiError(response.status, message)
}

function defaultMessage(status: number): string {
  switch (status) {
    case 400: return 'Requête invalide.'
    case 401: return 'Connexion requise.'
    case 403: return 'Action non autorisée.'
    case 404: return 'Introuvable.'
    case 409: return 'Conflit avec l\'état actuel.'
    case 503: return 'Service momentanément indisponible.'
    default: return 'Une erreur est survenue (' + status + ').'
  }
}

/** fetch() + token + renouvellement automatique + erreurs lisibles. */
export async function apiFetch(path: string, init: RequestInit = {}, retry = true): Promise<Response> {
  const headers = new Headers(init.headers)
  if (accessToken) headers.set('Authorization', 'Bearer ' + accessToken)

  const response = await fetch(path, { ...init, headers })
  if (response.status === 401 && retry && accessToken) {
    if (await refreshSession()) return apiFetch(path, init, false)
    onSessionExpired()
  }
  if (!response.ok) throw await errorFrom(response)
  return response
}

export async function apiJson<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await apiFetch(path, init)
  return (await response.json()) as T
}

/** Corps JSON pour POST / PUT. */
export function jsonBody(data: unknown): RequestInit {
  return { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data) }
}
