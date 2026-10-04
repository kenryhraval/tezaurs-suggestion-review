export type SuggestionStatus =
  | 'NEW'
  | 'READY_FOR_REVIEW'
  | 'GARBAGE'
  | 'COMPLETED'
  | 'IN_PROGRESS'
  | 'INVENTED'
  | 'ALREADY_EXISTS'
  | 'INSUFFICIENT_DATA'
  | 'NEEDS_EXPERT'

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
  id: number
  submittedTerm: string
  submittedDefinition: string
  usageExample: string | null
  source: string | null
  flagInfo: string | null
  notes: string | null
  contact: string | null
  status: SuggestionStatus
  createdAt: string
  updatedAt: string | null
}

export type Review = {
  id: string
  sourceSuggestionId: number
  reviewedTerm: string | null
  tezaursStatus: TezaursStatus
  matchedEntryId: number | null
  corpusStatus: CheckStatus
  canComplete: boolean
  completionBlockReason: CompletionBlockReason | null
  createdAt: string
  updatedAt: string | null
}

export type Meaning = {
  id: string
  reviewId: string
  supersedesMeaningId: string | null
  origin: MeaningOrigin
  gloss: string
  status: MeaningStatus
  createdAt: string
  updatedAt: string
}

export type CorpusExample = {
  id: string
  reviewId: string
  url: string
  createdAt: string
}
