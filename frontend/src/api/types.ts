// Types des réponses de l'API (miroir des records Java du backend).

export type VideoPolicy = 'PUBLIC' | 'UNLISTED' | 'PRIVATE'
export type TranscodeStatus = 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'
export type ReactionType = 'LIKE' | 'DISLIKE'
/** AUTO : image au tiers de la vidéo ; AUTO_1..3 : autres images proposées ; CUSTOM : image envoyée. */
export type ThumbnailChoice = 'AUTO' | 'AUTO_1' | 'AUTO_2' | 'AUTO_3' | 'CUSTOM'

export type DurationFilter = 'ANY' | 'SHORT' | 'MEDIUM' | 'LONG'
export type UploadedFilter = 'ANY' | 'DAY' | 'WEEK' | 'MONTH' | 'YEAR'
export type SortOrder = 'RECENT' | 'OLDEST' | 'VIEWS' | 'LIKES'

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface Me {
  id: string
  email: string
  name: string
  surname: string | null
  avatarUrl: string | null
  subscribers: number
  subscriptions: number
  videos: number
}

export interface UserSummary {
  id: string
  name: string | null
  surname: string | null
  avatarUrl: string | null    // null : pas de photo de profil (on affiche l'initiale)
}

export interface UserProfile extends UserSummary {
  subscribers: number
  videos: number
  subscribed: boolean
}

export interface Processing {
  progress: number
  step: string | null
  error: string | null
  updatedAt: string | null
}

export interface Video {
  id: string
  title: string
  description: string | null
  duration: number
  createdAt: string
  publishedAt: string | null   // null : brouillon, visible par son propriétaire seul
  views: number
  likes: number
  dislikes: number
  status: TranscodeStatus
  processing: Processing
  qualities: string[]
  hlsUrl: string | null
  policy: VideoPolicy
  owner: UserSummary
  myReaction: ReactionType | null
  thumbnail: { selected: ThumbnailChoice; options: ThumbnailChoice[] }
}

/** Ce qu'il faut pour afficher une carte de vidéo. Les champs optionnels dépendent de la liste. */
export interface VideoListItem {
  publicId: string
  title: string
  duration: number
  views: number
  likes?: number
  publishedAt: string
  ownerId?: string
  ownerName?: string | null
}

export interface OwnedVideo extends Omit<VideoListItem, 'publishedAt'> {
  createdAt: string
  publishedAt: string | null   // null : brouillon
  policy: VideoPolicy
  status: TranscodeStatus
  processing: Processing
}

export interface WatchedVideo {
  publicId: string
  title: string
  duration: number
  positionSeconds: number
  watchedAt: string
}

export interface ReactionResponse {
  likes: number
  dislikes: number
  myReaction: ReactionType | null
}

export type CommentSort = 'RECENT' | 'TOP'

export interface Comment {
  id: string
  parentId: string | null     // commentaire principal auquel celui-ci répond
  content: string
  createdAt: string
  editedAt: string | null
  likes: number
  dislikes: number
  replyCount: number
  author: UserSummary
  byVideoOwner: boolean       // écrit par l'auteur de la vidéo
  myReaction: ReactionType | null
}

/** Page de commentaires principaux ; totalComments compte aussi les réponses. */
export interface CommentPage extends Page<Comment> {
  totalComments: number
}

/** Corps de PATCH /api/videos/{id} : seuls les champs présents sont modifiés. */
export interface VideoUpdate {
  title?: string
  description?: string
  policy?: VideoPolicy
}
