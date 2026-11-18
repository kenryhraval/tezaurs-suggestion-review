export type CurrentUser = {
  username: string | null
}

export type ReviewStatus =
  | 'NEW'
  | 'READY_FOR_REVIEW'
  | 'GARBAGE'
  | 'COMPLETED'
  | 'IN_PROGRESS'
  | 'INVENTED'
  | 'ALREADY_EXISTS'
  | 'INSUFFICIENT_DATA'
  | 'NEEDS_EXPERT'

export type ReviewSort = 'NEWEST' | 'OLDEST' | 'MOST_SUGGESTIONS'

export type CaseType = 'UNKNOWN' | 'ENTRY' | 'SENSE'

export type SaveCaseType = {
  type: CaseType
  matchedEntryId: number | null
  meaningAlreadyExists?: boolean
}

export type CompletionBlockReason =
  | 'TEZAURS_CHECK_REQUIRED'
  | 'USAGE_CHECK_REQUIRED'
  | 'ENTRY_DRAFT_REQUIRED'
  | 'SENSE_DRAFT_REQUIRED'

export type ReviewListItem = {
  id: string
  publicId: string
  reviewedTerm: string
  status: ReviewStatus
  createdAt: string
  sourceCount: number
}

export type ReviewPageResponse = {
  items: ReviewListItem[]
  page: number
  totalPages: number
  totalItems: number
}

export type Review = {
  id: string
  publicId: string
  reviewedTerm: string
  status: ReviewStatus
  lastStatusChangedBy: string | null
  type: CaseType
  matchedEntryId: number | null
  usageDecision: UsageDecision
  usageNote: string | null
  usageDecidedBy: string | null
  usageDecidedAt: string | null
  canComplete: boolean
  completionBlockReason: CompletionBlockReason | null
  createdAt: string
  sources: ReviewSource[]
}

export type ReviewSource = {
  id: number
  submittedTerm: string
  submittedDefinition: string
  usageExample: string | null
  source: string | null
  flagInfo: string | null
  notes: string | null
  contact: string | null
  createdAt: string
}

export type DuplicateReviewCandidate = {
  id: string
  reviewedTerm: string
  status: ReviewStatus
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

export type TezaursOption = {
  id: number
  code: string
  label: string
}

export type TezaursParadigm = {
  id: number
  code: string
  label: string
  stems: number
  legacyNo: number | null
  data: Record<string, unknown> | null
}

export type TezaursFlagDefinition = {
  name: string
  label: string
  description: string | null
  multiple: boolean
  permittedValues: string
  scopes: string[]
  values: string[]
}

export type RestrictionFrequency = {
  id: number
  code: string
  label: string | null
}

export type TezaursFormOptions = {
  entryTypes: TezaursOption[]
  lexemeRoles: TezaursOption[]
  paradigms: TezaursParadigm[]
  flags: TezaursFlagDefinition[]
  restrictionTypes: TezaursOption[]
  restrictionFrequencies: RestrictionFrequency[]
}

export type UsageDecision = 'NOT_CHECKED' | 'USED' | 'NOT_CONFIRMED' | 'INVENTED'

export type SaveUsageEvidence = {
  source: 'CORPUS' | 'EXTERNAL'
  corpus: string | null
  tokenNumber: number | null
  query: string
  citation: string
  url: string
  reference: string | null
  note: string | null
}

export type UsageEvidence = SaveUsageEvidence & {
  corpusLabel: string | null
  sourceLabel: string
  sourceUrl: string | null
  id: string
  createdBy: string | null
  createdAt: string
}

export type CorpusExample = {
  tokenNumber: number
  left: string
  match: string
  right: string
  citation: string
  reference: string
  sourceLabel: string
  sourceUrl: string | null
}

export type CorpusSearchResult = {
  corpus: string
  label: string
  query: string
  page: number
  pageSize: number
  total: number
  url: string
  examples: CorpusExample[]
}

export type CorpusOption = {
  id: string
  label: string
}
