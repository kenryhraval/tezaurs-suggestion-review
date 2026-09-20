export type SuggestionStatus =
  | 'NEW'
  | 'IN_PROGRESS'
  | 'POSTPONED'
  | 'NEEDS_EXPERT'
  | 'COMPLETED'
  | 'GARBAGE'

export type CheckStatus = 'NOT_CHECKED' | 'FOUND' | 'NOT_FOUND'

export type MeaningStatus = 'PROPOSED' | 'APPROVED' | 'REJECTED' | 'SUPERSEDED'

export type MeaningOrigin = 'SUBMITTER' | 'GENERATED' | 'REVIEWER' | 'EXISTING'

export type Suggestion = {
  id: string
  submittedTerm: string
  reviewedTerm: string | null
  usageExample: string | null
  notes: string | null
  submitterName: string | null
  submitterEmail: string | null
  status: SuggestionStatus
  tezaursStatus: CheckStatus
  matchedEntryId: number | null
  corpusStatus: CheckStatus
  createdAt: string
  updatedAt: string
}

export type Meaning = {
  id: string
  suggestionId: string
  supersedesMeaningId: string | null
  origin: MeaningOrigin
  gloss: string
  status: MeaningStatus
  createdAt: string
  updatedAt: string
}

export type CreateSuggestionInput = {
  term: string
  definition: string
  usageExample: string | null
  notes: string | null
  submitterName: string | null
  submitterEmail: string | null
}
