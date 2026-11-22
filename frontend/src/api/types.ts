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

export type ReviewType = 'UNKNOWN' | 'ENTRY' | 'SENSE'

export type SaveReviewType = {
  type: ReviewType
  matchedEntryId: number | null
  meaningAlreadyExists?: boolean
}

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
  type: ReviewType
  matchedEntryId: number | null
  usageDecision: UsageDecision | null
  usageNote: string | null
  usageDecidedBy: string | null
  usageDecidedAt: string | null
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
}

export type EntryType = 'WORD' | 'MULTIWORD_EXPRESSION' | 'WORD_PART'

export type EntryDraft = {
  id: string
  heading: string
  humanKey: string
  type: EntryType
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
}

export type SaveEntryDraft = {
  heading: string
  type: EntryType
  etymology: string | null
  normativeComment: string | null
  scientificName: string | null
  source: string | null
  notes: string | null
  flags: FlagMap
}

export type Sense = {
  approved: boolean
  existingSenseId: number | null
  exampleIds: string[]
  id: string
  gloss: string
  parentSenseId: string | null
  orderNo: number
  flags: FlagMap
}

export type SaveSense = {
  id?: string
  gloss: string
  approved?: boolean
  parentSenseId?: string | null
  flags?: FlagMap
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

export type TezaursFormOptions = {
  lexemeRoles: TezaursOption[]
  paradigms: TezaursParadigm[]
  flags: TezaursFlagDefinition[]
}

export type UsageDecision = 'USED' | 'NOT_CONFIRMED' | 'INVENTED'

export type SaveExample = {
  source: 'CORPUS' | 'EXTERNAL'
  corpus: string | null
  tokenNumber: number | null
  query: string
  citation: string
  matchStart?: number | null
  matchEnd?: number | null
  url: string
  reference: string | null
  note: string | null
}

export type Example = SaveExample & {
  senseId: string | null
  approved: boolean
  corpusLabel: string | null
  sourceLabel: string
  sourceUrl: string | null
  id: string
  createdBy: string | null
  createdAt: string
}

export type SelectCorpusExamples = {
  corpus: string
  query: string
  exactOnly: boolean
  count: number | null
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

export type SenseModelOption = {
  id: string
  label: string
  defaultModel: boolean
}

export type ModelOperation = 'clustering' | 'definitions'
