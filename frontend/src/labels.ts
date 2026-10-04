import type { MeaningOrigin, SuggestionStatus } from './types'

export const suggestionStatusLabels: Record<SuggestionStatus, string> = {
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

export const suggestionStatusTransitions: Record<SuggestionStatus, SuggestionStatus[]> = {
  NEW: ['READY_FOR_REVIEW', 'IN_PROGRESS', 'GARBAGE'],
  READY_FOR_REVIEW: ['IN_PROGRESS', 'COMPLETED', 'NEW'],
  GARBAGE: ['NEW'],
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
  ALREADY_EXISTS: [],
  INSUFFICIENT_DATA: ['IN_PROGRESS', 'GARBAGE'],
  NEEDS_EXPERT: ['IN_PROGRESS'],
  COMPLETED: [],
}

export const meaningOriginLabels: Record<MeaningOrigin, string> = {
  SUBMITTER: 'Iesniedzējs',
  GENERATED: 'Ģenerēta',
  REVIEWER: 'Redaktors',
  EXISTING: 'Esoša Tēzaurā',
}
