export type SuggestionStatus =
  | 'NEW'
  | 'IN_PROGRESS'
  | 'POSTPONED'
  | 'NEEDS_EXPERT'
  | 'COMPLETED'
  | 'GARBAGE'

export type CheckStatus = 'NOT_CHECKED' | 'FOUND' | 'NOT_FOUND'

export type TezaursStatus =
  | 'NOT_CHECKED'
  | 'FOUND'
  | 'MEANING_FOUND'
  | 'MEANING_NOT_FOUND'
  | 'NOT_FOUND'

export type MeaningStatus = 'CURRENT' | 'SUPERSEDED'

export type CompletionBlockReason =
  | 'TEZAURS_CHECK_REQUIRED'
  | 'MEANING_CHECK_REQUIRED'
  | 'CORPUS_CHECK_REQUIRED'

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
  tezaursStatus: TezaursStatus
  matchedEntryId: number | null
  corpusStatus: CheckStatus
  canComplete: boolean
  completionBlockReason: CompletionBlockReason | null
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

export type CorpusExample = {
  id: string
  suggestionId: string
  url: string
  createdAt: string
}

export type CreateSuggestionInput = {
  term: string
  definition: string
  usageExample: string | null
  notes: string | null
  submitterName: string | null
  submitterEmail: string | null
}
