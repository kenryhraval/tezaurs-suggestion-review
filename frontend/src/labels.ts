import type { MeaningOrigin, SuggestionStatus } from './types'

export const suggestionStatusLabels: Record<SuggestionStatus, string> = {
  NEW: 'Jauns',
  IN_PROGRESS: 'Izskatīšanā',
  POSTPONED: 'Atlikts',
  NEEDS_EXPERT: 'Vajadzīgs eksperts',
  COMPLETED: 'Pabeigts',
  GARBAGE: 'Miskaste',
}

export const meaningOriginLabels: Record<MeaningOrigin, string> = {
  SUBMITTER: 'Iesniedzējs',
  GENERATED: 'Ģenerēta',
  REVIEWER: 'Redaktors',
  EXISTING: 'Esoša Tēzaurā',
}
