// Icônes Font Awesome (version gratuite, SVG). Seules les icônes listées ici sont incluses dans
// le site : pour en utiliser une nouvelle, l'importer puis l'ajouter à library.add().
// Dans les gabarits : <v-icon icon="fas fa-play" /> ou <v-btn prepend-icon="far fa-thumbs-up">.
//   fas = style plein (solid), far = style contour (regular)

import { library } from '@fortawesome/fontawesome-svg-core'
import {
  faBookmark as farBookmark, faCircle as farCircle, faCircleDot, faClone, faPenToSquare as farPenToSquare,
  faSquare as farSquare, faThumbsDown as farThumbsDown, faThumbsUp as farThumbsUp, faTrashCan as farTrashCan,
} from '@fortawesome/free-regular-svg-icons'
import {
  faArrowRightFromBracket, faBars, faCamera, faCaretDown, faCheck, faChevronDown, faChevronLeft, faChevronRight,
  faChevronUp, faCircleCheck, faCircleExclamation, faCircleInfo, faCirclePlay, faCircleXmark, faClockRotateLeft,
  faCloudArrowUp, faCompass, faCompress, faExpand, faEye, faEyeSlash, faFilm, faGear, faImage, faMagnifyingGlass,
  faPause, faPen, faPlay, faPlus, faReply, faRotateLeft, faRotateRight, faRightToBracket, faShareFromSquare,
  faSquareCheck, faThumbsDown, faThumbsUp, faTrashCan, faTriangleExclamation, faUser, faUserCheck, faUserPlus,
  faUsers, faVolumeHigh, faVolumeLow, faVolumeXmark, faXmark,
} from '@fortawesome/free-solid-svg-icons'

library.add(
  // Utilisées par les composants Vuetify eux-mêmes (listes déroulantes, alertes, cases...)
  faCaretDown, faCheck, faChevronDown, faChevronLeft, faChevronRight, faChevronUp, faCircleCheck,
  faCircleExclamation, faCircleInfo, faCircleXmark, faSquareCheck, faTriangleExclamation, faXmark,
  farCircle, faCircleDot, farSquare,
  // Utilisées par l'application
  faArrowRightFromBracket, faBars, faCamera, faCirclePlay, faClockRotateLeft, faCloudArrowUp, faCompass, faCompress,
  faExpand, faEye, faEyeSlash, faFilm, faGear, faImage, faMagnifyingGlass, faPause, faPen, faPlay, faPlus, faReply,
  faRotateLeft, faRotateRight, faRightToBracket, faShareFromSquare, faThumbsDown, faThumbsUp, faTrashCan, faUser,
  faUserCheck, faUserPlus, faUsers, faVolumeHigh, faVolumeLow, faVolumeXmark, faClone,
  farBookmark, farPenToSquare, farThumbsDown, farThumbsUp, farTrashCan,
)
