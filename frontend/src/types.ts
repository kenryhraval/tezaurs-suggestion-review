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

export type SuggestionSort = 'NEWEST' | 'OLDEST' | 'MOST_SUGGESTIONS'

export type CheckStatus = 'NOT_CHECKED' | 'FOUND' | 'NOT_FOUND'

export type TezaursStatus =
  | 'NOT_CHECKED'
  | 'FOUND'
  | 'MEANING_FOUND'
  | 'MEANING_NOT_FOUND'
  | 'NOT_FOUND'

export type CompletionBlockReason =
  | 'TEZAURS_CHECK_REQUIRED'
  | 'MEANING_CHECK_REQUIRED'
  | 'CORPUS_CHECK_REQUIRED'
  | 'ENTRY_DRAFT_REQUIRED'
  | 'SENSE_DRAFT_REQUIRED'

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
  sourceCount: number
}

export type SuggestionPage = {
  items: Suggestion[]
  page: number
  totalPages: number
  totalItems: number
}

export type Review = {
  id: string
  reviewedTerm: string
  tezaursStatus: TezaursStatus
  matchedEntryId: number | null
  corpusStatus: CheckStatus
  canComplete: boolean
  completionBlockReason: CompletionBlockReason | null
  createdAt: string
  updatedAt: string | null
}

export type DuplicateReviewCandidate = {
  id: string
  reviewedTerm: string
  status: SuggestionStatus
  sourceCount: number
}

export type TezaursSense = {
  id: number
  parentSenseId: number | null
  gloss: string | null
  orderNo: number | null
}

export type TezaursEntryCandidate = {
  id: number
  heading: string
  humanKey: string
  senses: TezaursSense[]
}

export type CorpusExample = {
  id: string
  reviewId: string
  url: string
  createdAt: string
}

export type FlagValue = string | string[] | null
export type FlagMap = Record<string, FlagValue>

export type LexemeDraft = {
  id: string | null
  lemma: string
  primary: boolean
  roleId: number
  paradigmId: number | null
  stem1: string | null
  stem2: string | null
  stem3: string | null
  pronunciations: string[]
  flags: FlagMap
  structuralRestrictions: Record<string, unknown> | null
}

export type EntryDraft = {
  id: string
  heading: string
  humanKey: string
  typeId: number
  etymology: string | null
  normativeComment: string | null
  scientificName: string | null
  source: string | null
  notes: string | null
  flags: FlagMap
  lexemes: LexemeDraft[]
}

export type SaveLexemeDraft = {
  lemma: string
  primary: boolean
  roleId: number
  paradigmId: number | null
  stem1: string | null
  stem2: string | null
  stem3: string | null
  pronunciations: string[]
  flags: FlagMap
  structuralRestrictions: Record<string, unknown> | null
}

export type SaveEntryDraft = {
  heading: string
  typeId: number
  etymology: string | null
  normativeComment: string | null
  scientificName: string | null
  source: string | null
  notes: string | null
  flags: FlagMap
}

export type SenseDraft = {
  id: string
  gloss: string
  parentDraftSenseId: string | null
  parentTezaursSenseId: number | null
  orderNo: number
  flags: FlagMap
  structuralRestrictions: Record<string, unknown> | null
}

export type SaveSenseDraft = {
  id?: string
  gloss: string
  parentDraftSenseId: string | null
  parentTezaursSenseId: number | null
  flags: FlagMap
  structuralRestrictions: Record<string, unknown> | null
}

export type TezaursFormOptions = {
  entryTypes: Array<{ id: number; code: string; label: string }>
  lexemeRoles: Array<{ id: number; code: string; label: string }>
  paradigms: Array<{ id: number; code: string; label: string; stems: number }>
  flags: Array<{
    name: string
    label: string
    description: string | null
    multiple: boolean
    permittedValues: string
    scopes: string[]
    values: string[]
  }>
}

export type TezaursEntryDetails = {
  id: number
  heading: string
  humanKey: string
  typeId: number
  type: string | null
  hidden: boolean
  notes: string | null
  data: Record<string, unknown> | null
  lexemes: Array<{
    id: number
    lemma: string
    roleId: number
    role: string | null
    paradigmId: number | null
    paradigm: string | null
    stem1: string | null
    stem2: string | null
    stem3: string | null
    hidden: boolean
    data: Record<string, unknown> | null
  }>
  senses: Array<{
    id: number
    parentSenseId: number | null
    orderNo: number | null
    senseTag: string | null
    gloss: string | null
    hidden: boolean
    data: Record<string, unknown> | null
    examples: TezaursExample[]
  }>
  examples: TezaursExample[]
  sources: Array<{
    id: number
    senseId: number | null
    lexemeId: number | null
    abbreviation: string | null
    title: string | null
    url: string | null
  }>
}

export type TezaursExample = {
  id: number
  content: string
  hidden: boolean
  data: Record<string, unknown> | null
}
