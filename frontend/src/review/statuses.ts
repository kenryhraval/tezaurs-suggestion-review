import type { ReviewType, ReviewStatus } from '../api/types'

export const reviewTypeLabels: Record<ReviewType, string> = {
  UNKNOWN: 'Nav noteikts',
  ENTRY: 'Šķirklis',
  SENSE: 'Nozīme',
}

export const reviewStatusLabels: Record<ReviewStatus, string> = {
  NEW: 'Jauns',
  READY_FOR_REVIEW: 'Skatāms',
  GARBAGE: 'Atkritums',
  COMPLETED: 'Gatavs',
  IN_PROGRESS: 'Apstrādē',
  INVENTED: 'Izdoma',
  ALREADY_EXISTS: 'Jau ir',
  INSUFFICIENT_DATA: 'Trūkst datu',
  NEEDS_EXPERT: 'Vajag ekspertu',
}

export const reviewStatusTransitions: Record<ReviewStatus, ReviewStatus[]> = {
  NEW: ['READY_FOR_REVIEW', 'IN_PROGRESS', 'GARBAGE'],
  READY_FOR_REVIEW: ['IN_PROGRESS', 'COMPLETED', 'GARBAGE'],
  GARBAGE: ['READY_FOR_REVIEW'],
  IN_PROGRESS: [
    'INVENTED',
    'ALREADY_EXISTS',
    'INSUFFICIENT_DATA',
    'NEEDS_EXPERT',
    'COMPLETED',
    'READY_FOR_REVIEW',
    'GARBAGE',
  ],
  INVENTED: ['IN_PROGRESS', 'GARBAGE'],
  ALREADY_EXISTS: ['IN_PROGRESS'],
  INSUFFICIENT_DATA: ['IN_PROGRESS', 'GARBAGE'],
  NEEDS_EXPERT: ['IN_PROGRESS'],
  COMPLETED: [],
}
