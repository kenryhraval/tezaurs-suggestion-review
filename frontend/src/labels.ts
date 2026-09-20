import type {
  CheckStatus,
  MeaningOrigin,
  MeaningStatus,
  SuggestionStatus,
} from './types'

export const suggestionStatuses: SuggestionStatus[] = [
  'NEW',
  'IN_PROGRESS',
  'POSTPONED',
  'NEEDS_EXPERT',
  'COMPLETED',
  'GARBAGE',
]

export const checkStatuses: CheckStatus[] = [
  'NOT_CHECKED',
  'FOUND',
  'NOT_FOUND',
]

export const suggestionStatusLabels: Record<SuggestionStatus, string> = {
  NEW: 'Jauns',
  IN_PROGRESS: 'Izskatīšanā',
  POSTPONED: 'Atlikts',
  NEEDS_EXPERT: 'Vajadzīgs eksperts',
  COMPLETED: 'Pabeigts',
  GARBAGE: 'Nederīgs',
}

export const checkStatusLabels: Record<CheckStatus, string> = {
  NOT_CHECKED: 'Nav pārbaudīts',
  FOUND: 'Atrasts',
  NOT_FOUND: 'Nav atrasts',
}

export const meaningStatusLabels: Record<MeaningStatus, string> = {
  PROPOSED: 'Ierosināta',
  APPROVED: 'Apstiprināta',
  REJECTED: 'Noraidīta',
  SUPERSEDED: 'Aizstāta',
}

export const meaningOriginLabels: Record<MeaningOrigin, string> = {
  SUBMITTER: 'Iesniedzējs',
  GENERATED: 'Ģenerēta',
  REVIEWER: 'Redaktors',
  EXISTING: 'Esoša Tēzaurā',
}
