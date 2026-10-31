// Une fonction par route de l'API : les pages n'écrivent jamais d'URL elles-mêmes.

import { ApiError, apiFetch, apiJson, getAccessToken, jsonBody, refreshSession } from './client'
import type {
  Comment, CommentPage, CommentSort, DurationFilter, Me, OwnedVideo, Page, ReactionResponse, ReactionType, SortOrder,
  ThumbnailChoice, TokenResponse, UploadedFilter, UserProfile, UserSummary, Video, VideoListItem, VideoUpdate,
  WatchedVideo,
} from './types'

const page = (n = 0, size = 24) => `page=${n}&size=${size}`
const enc = encodeURIComponent

// --- Authentification ---

export const auth = {
  register: (data: { email: string; password: string; name: string; surname?: string }) =>
    apiJson<Me>('/api/auth/register', { method: 'POST', ...jsonBody(data) }),
  login: (email: string, password: string) =>
    apiJson<TokenResponse>('/api/auth/login', { method: 'POST', ...jsonBody({ email, password }) }),
  refresh: refreshSession,
  logout: () => apiFetch('/api/auth/logout', { method: 'POST' }),
}

// --- Vidéos ---

export interface SearchParams {
  q?: string
  duration?: DurationFilter
  uploaded?: UploadedFilter
  sort?: SortOrder
  page?: number
}

export const videos = {
  search: (p: SearchParams) => {
    const query = new URLSearchParams({
      q: p.q ?? '', duration: p.duration ?? 'ANY', uploaded: p.uploaded ?? 'ANY', sort: p.sort ?? 'RECENT',
    })
    return apiJson<Page<VideoListItem>>(`/api/videos?${query}&${page(p.page)}`)
  },
  get: (id: string) => apiJson<Video>(`/api/videos/${enc(id)}`),
  view: (id: string) => apiFetch(`/api/videos/${enc(id)}/view`, { method: 'POST' }),
  react: (id: string, type: ReactionType) =>
    apiJson<ReactionResponse>(`/api/videos/${enc(id)}/reaction`, { method: 'PUT', ...jsonBody({ type }) }),
  removeReaction: (id: string) =>
    apiJson<ReactionResponse>(`/api/videos/${enc(id)}/reaction`, { method: 'DELETE' }),
  retry: (id: string) => apiJson<Video>(`/api/videos/${enc(id)}/retry`, { method: 'POST' }),
  update: (id: string, changes: VideoUpdate) =>
    apiJson<Video>(`/api/videos/${enc(id)}`, { method: 'PATCH', ...jsonBody(changes) }),
  remove: (id: string) => apiFetch(`/api/videos/${enc(id)}`, { method: 'DELETE' }),
  /** Publie un brouillon (une fois la vidéo traitée). */
  publish: (id: string) => apiJson<Video>(`/api/videos/${enc(id)}/publish`, { method: 'POST' }),
  thumbnailUrl: (id: string) => `/api/videos/${enc(id)}/thumbnail.jpg`,
  /** Aperçu d'une miniature possible ; v force le rechargement après un nouvel envoi. */
  thumbnailOptionUrl: (id: string, choice: ThumbnailChoice, v = '') =>
    `/api/videos/${enc(id)}/thumbnails/${choice.toLowerCase()}.jpg${v ? '?v=' + enc(v) : ''}`,
  chooseThumbnail: (id: string, choice: ThumbnailChoice) =>
    apiJson<Video>(`/api/videos/${enc(id)}/thumbnail`, { method: 'PUT', ...jsonBody({ choice }) }),
  uploadThumbnail: (id: string, file: File) => {
    const data = new FormData()
    data.append('file', file)
    // Pas de Content-Type à la main : le navigateur ajoute "multipart/form-data; boundary=..."
    return apiJson<Video>(`/api/videos/${enc(id)}/thumbnail`, { method: 'POST', body: data })
  },

  /**
   * Upload avec suivi de l'envoi. fetch() ne donne pas la progression d'un envoi :
   * on utilise XMLHttpRequest, qui a l'événement upload.onprogress.
   */
  upload: (form: { title: string; file: File }, onProgress: (percent: number) => void): Promise<Video> => {
    const data = new FormData()
    data.append('title', form.title)
    data.append('file', form.file)

    const send = () => new Promise<XMLHttpRequest>((resolve, reject) => {
      const xhr = new XMLHttpRequest()
      xhr.open('POST', '/api/videos')
      const token = getAccessToken()
      if (token) xhr.setRequestHeader('Authorization', 'Bearer ' + token)
      xhr.upload.onprogress = (e) => { if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100)) }
      xhr.onload = () => resolve(xhr)
      xhr.onerror = () => reject(new ApiError(0, 'Le serveur est injoignable.'))
      xhr.send(data)
    })

    return send()
      .then(async (xhr) => (xhr.status === 401 && await refreshSession()) ? send() : xhr)
      .then((xhr) => {
        let body: { message?: string } | null = null
        try { body = JSON.parse(xhr.responseText) } catch { /* corps vide */ }
        if (xhr.status >= 200 && xhr.status < 300) return body as unknown as Video
        throw new ApiError(xhr.status, body?.message ?? 'L\'envoi a échoué (' + xhr.status + ').')
      })
  },
}

// --- Commentaires ---

export const comments = {
  list: (videoId: string, sort: CommentSort, n = 0) =>
    apiJson<CommentPage>(`/api/videos/${enc(videoId)}/comments?sort=${sort}&${page(n, 20)}`),
  replies: (id: string, n = 0) => apiJson<Page<Comment>>(`/api/comments/${enc(id)}/replies?${page(n, 20)}`),
  reply: (id: string, content: string) =>
    apiJson<Comment>(`/api/comments/${enc(id)}/replies`, { method: 'POST', ...jsonBody({ content }) }),
  create: (videoId: string, content: string) =>
    apiJson<Comment>(`/api/videos/${enc(videoId)}/comments`, { method: 'POST', ...jsonBody({ content }) }),
  edit: (id: string, content: string) =>
    apiJson<Comment>(`/api/comments/${enc(id)}`, { method: 'PUT', ...jsonBody({ content }) }),
  remove: (id: string) => apiFetch(`/api/comments/${enc(id)}`, { method: 'DELETE' }),
  react: (id: string, type: ReactionType) =>
    apiJson<ReactionResponse>(`/api/comments/${enc(id)}/reaction`, { method: 'PUT', ...jsonBody({ type }) }),
  removeReaction: (id: string) =>
    apiJson<ReactionResponse>(`/api/comments/${enc(id)}/reaction`, { method: 'DELETE' }),
}

// --- Utilisateurs ---

export const users = {
  me: () => apiJson<Me>('/api/users/me'),
  uploadAvatar: (file: File) => {
    const data = new FormData()
    data.append('file', file)
    return apiJson<Me>('/api/users/me/avatar', { method: 'PUT', body: data })
  },
  deleteAvatar: () => apiJson<Me>('/api/users/me/avatar', { method: 'DELETE' }),
  myVideos: (n = 0) => apiJson<Page<OwnedVideo>>(`/api/users/me/videos?${page(n)}`),
  likes: (n = 0) => apiJson<Page<VideoListItem>>(`/api/users/me/likes?${page(n)}`),
  history: (n = 0) => apiJson<Page<WatchedVideo>>(`/api/users/me/history?${page(n)}`),
  clearHistory: () => apiFetch('/api/users/me/history', { method: 'DELETE' }),
  saveWatchPosition: (id: string, positionSeconds: number) =>
    apiFetch(`/api/users/me/history/${enc(id)}`, { method: 'PUT', ...jsonBody({ positionSeconds }) }),
  feed: (n = 0) => apiJson<Page<VideoListItem>>(`/api/users/me/feed?${page(n)}`),
  subscriptions: () => apiJson<Page<UserSummary>>(`/api/users/me/subscriptions?page=0&size=100`),
  subscribe: (channelId: string) => apiFetch(`/api/users/me/subscriptions/${enc(channelId)}`, { method: 'PUT' }),
  unsubscribe: (channelId: string) => apiFetch(`/api/users/me/subscriptions/${enc(channelId)}`, { method: 'DELETE' }),
  profile: (id: string) => apiJson<UserProfile>(`/api/users/${enc(id)}`),
  channelVideos: (id: string, n = 0) => apiJson<Page<VideoListItem>>(`/api/users/${enc(id)}/videos?${page(n)}`),
}
