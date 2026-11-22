import type {
  SenseModelOption,
  DefinitionInput,
  GeneratedDefinition,
  CurrentUser,
  ReviewType,
  DuplicateReviewCandidate,
  EntryDraft,
  Review,
  ReviewPageResponse,
  SaveSense,
  Sense,
  ReviewSort,
  ReviewStatus,
  SaveEntryDraft,
  SaveLexemeDraft,
  LexemeDraft,
  TezaursEntryCandidate,
  TezaursSense,
  TezaursFormOptions,
  SaveReviewType,
  UsageDecision,
  Example,
  SaveExample,
  SelectCorpusExamples,
  CorpusSearchResult,
  CorpusOption,
} from './types'

async function request(path: string, options?: RequestInit): Promise<Response> {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })

  if (!response.ok) {
    if (response.status === 400) {
      throw new Error('Pārbaudiet ievadītos datus.')
    }
    if (response.status === 404) {
      throw new Error('Ieraksts netika atrasts.')
    }
    if (response.status === 409 || response.status === 502) {
      const error = await response.json().catch(() => null)
      if (typeof error?.message === 'string' && error.message.trim()) {
        throw new Error(error.message)
      }
      throw new Error('Darbību nevar izpildīt pašreizējā stāvoklī.')
    }
    throw new Error('Pieprasījumu neizdevās izpildīt.')
  }

  return response
}

export async function getCurrentUser(signal?: AbortSignal): Promise<CurrentUser> {
  const response = await request('/api/me', { signal })
  return response.json()
}

export async function getSuggestionCounts(): Promise<{ suggestions: number; cases: number }> {
  const response = await request('/api/suggestions/counts')
  return response.json()
}

export async function getReviews(
  status: ReviewStatus | null,
  query: string,
  page: number,
  size: number,
  sort: ReviewSort,
  types: ReviewType[] = [],
): Promise<ReviewPageResponse> {
  const parameters = new URLSearchParams({
    query,
    page: String(page),
    size: String(size),
    sort,
  })
  if (status !== null) {
    parameters.set('status', status)
  }
  for (const type of types) {
    parameters.append('types', type)
  }
  const response = await request(`/api/reviews?${parameters}`)
  return response.json()
}

export async function detachSuggestion(reviewId: string, suggestionId: number): Promise<Review> {
  const response = await request(`/api/reviews/${reviewId}/suggestions/${suggestionId}`, {
    method: 'DELETE',
  })
  return response.json()
}

export async function changeReviewStatus(
  id: string,
  status: ReviewStatus,
): Promise<Review> {
  const response = await request(`/api/reviews/${id}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  })
  return response.json()
}

export async function getDuplicateReviewCandidates(
  query: string,
  excludedCaseId: string,
): Promise<DuplicateReviewCandidate[]> {
  const parameters = new URLSearchParams({ query, excludedCaseId })
  const response = await request(`/api/reviews/duplicate-candidates?${parameters}`)
  return response.json()
}

export async function mergeReviewCases(sourceCaseId: string, reviewId: string): Promise<Review> {
  const response = await request(`/api/reviews/${sourceCaseId}/duplicate`, {
    method: 'POST',
    body: JSON.stringify({ reviewId }),
  })
  return response.json()
}

export async function getReviewByPublicId(publicId: string, signal?: AbortSignal): Promise<Review> {
  const response = await request(`/api/reviews/by-public-id/${publicId}`, { signal })
  return response.json()
}

export async function updateCaseTitle(caseId: string, title: string): Promise<Review> {
  const response = await request(`/api/reviews/${caseId}/term-correction`, {
    method: 'POST',
    body: JSON.stringify({ reviewedTerm: title }),
  })
  return response.json()
}

export async function saveReviewType(reviewId: string, selection: SaveReviewType): Promise<Review> {
  const response = await request(`/api/reviews/${reviewId}/type`, {
    method: 'POST',
    body: JSON.stringify(selection),
  })
  return response.json()
}

export async function getTezaursCandidates(query: string): Promise<TezaursEntryCandidate[]> {
  const response = await request(`/api/tezaurs/entries?query=${encodeURIComponent(query)}`)
  return response.json()
}

export async function getEntryDraft(reviewId: string): Promise<EntryDraft | null> {
  const response = await request(`/api/reviews/${reviewId}/entry`)
  if (response.status === 204) {
    return null
  }
  return response.json()
}

export async function createEntryDraft(
  reviewId: string,
): Promise<{ draft: EntryDraft; review: Review }> {
  const response = await request(
    `/api/reviews/${reviewId}/entry`,
    { method: 'POST' },
  )
  return response.json()
}

export async function updateEntryDraft(
  reviewId: string,
  draft: SaveEntryDraft,
): Promise<{ draft: EntryDraft; review: Review }> {
  const response = await request(
    `/api/reviews/${reviewId}/entry`,
    { method: 'PUT', body: JSON.stringify(draft) },
  )
  return response.json()
}

export async function createLexemeDraft(
  reviewId: string,
  lexeme: SaveLexemeDraft,
): Promise<{ lexeme: LexemeDraft; review: Review }> {
  const response = await request(
    `/api/reviews/${reviewId}/entry/lexemes`,
    { method: 'POST', body: JSON.stringify(lexeme) },
  )
  return response.json()
}

export async function updateLexemeDraft(
  reviewId: string,
  lexemeId: string,
  lexeme: SaveLexemeDraft,
): Promise<{ lexeme: LexemeDraft; review: Review }> {
  const response = await request(
    `/api/reviews/${reviewId}/entry/lexemes/${lexemeId}`,
    { method: 'PUT', body: JSON.stringify(lexeme) },
  )
  return response.json()
}

export async function deleteLexemeDraft(
  reviewId: string,
  lexemeId: string,
): Promise<Review> {
  const response = await request(
    `/api/reviews/${reviewId}/entry/lexemes/${lexemeId}`,
    { method: 'DELETE' },
  )
  return response.json()
}

export async function getTezaursFormOptions(): Promise<TezaursFormOptions> {
  const response = await request('/api/tezaurs/form-options')
  return response.json()
}

export async function getTezaursEntry(entryId: number): Promise<TezaursEntryCandidate> {
  const response = await request(`/api/tezaurs/entries/${entryId}`)
  return response.json()
}

export async function getTezaursSenses(entryId: number): Promise<TezaursSense[]> {
  const response = await request(`/api/tezaurs/entries/${entryId}/senses`)
  return response.json()
}

export async function getSenses(reviewId: string): Promise<Sense[]> {
  const response = await request(`/api/reviews/${reviewId}/senses`)
  return response.json()
}

export async function getSenseDrafts(reviewId: string): Promise<Sense[]> {
  const senses = await getSenses(reviewId)
  return senses.filter((sense) => sense.approved && sense.existingSenseId === null)
}

export async function saveSense(reviewId: string, sense: SaveSense): Promise<{ sense: Sense; review: Review }> {
  const path = `/api/reviews/${reviewId}/senses`
  const response = await request(sense.id ? `${path}/${sense.id}` : path, {
    method: sense.id ? 'PUT' : 'POST',
    body: JSON.stringify({
      gloss: sense.gloss,
      approved: sense.approved,
      parentDraftSenseId: sense.parentDraftSenseId,
      parentTezaursSenseId: sense.parentTezaursSenseId,
      flags: sense.flags,
    }),
  })
  return response.json()
}

export async function deleteSense(reviewId: string, senseId: string): Promise<Review> {
  const response = await request(`/api/reviews/${reviewId}/senses/${senseId}`, { method: 'DELETE' })
  return response.json()
}

export async function assignExampleSense(reviewId: string, exampleId: string, senseId: string | null): Promise<Example> {
  const response = await request(`/api/reviews/${reviewId}/usage/examples/${exampleId}/sense`, {
    method: 'PUT', body: JSON.stringify({ senseId }),
  })
  return response.json()
}

export async function getExamplesForCase(reviewId: string): Promise<Example[]> {
  const response = await request(`/api/reviews/${reviewId}/usage/examples`)
  return response.json()
}

export async function saveExamples(reviewId: string, examples: SaveExample[]): Promise<Example[]> {
  const response = await request(`/api/reviews/${reviewId}/usage/examples/batch`, {
    method: 'POST', body: JSON.stringify(examples),
  })
  return response.json()
}

export async function deleteExample(reviewId: string, exampleId: string): Promise<void> {
  await request(`/api/reviews/${reviewId}/usage/examples/${exampleId}`, { method: 'DELETE' })
}

export async function deleteCorpusExamples(reviewId: string, corpus: string): Promise<Example[]> {
  const parameters = new URLSearchParams({ corpus })
  const response = await request(`/api/reviews/${reviewId}/usage/examples?${parameters}`, { method: 'DELETE' })
  return response.json()
}

export async function selectCorpusExamples(reviewId: string, selection: SelectCorpusExamples): Promise<Example[]> {
  const response = await request(`/api/reviews/${reviewId}/usage/examples/select`, {
    method: 'POST', body: JSON.stringify(selection),
  })
  return response.json()
}

export async function saveUsageDecision(reviewId: string, decision: UsageDecision): Promise<Review> {
  const response = await request(`/api/reviews/${reviewId}/usage/decision`, {
    method: 'POST', body: JSON.stringify({ decision }),
  })
  return response.json()
}

export async function searchCorpus(
  query: string, corpus: string, page: number, exactOnly: boolean,
): Promise<CorpusSearchResult> {
  const parameters = new URLSearchParams({ query, page: String(page), exactOnly: String(exactOnly) })
  const response = await request(`/api/corpora/${corpus}/search?${parameters}`)
  return response.json()
}

export async function getCorpora(): Promise<CorpusOption[]> {
  const response = await request('/api/corpora')
  return response.json()
}

export async function regroupSenses(reviewId: string, modelId: string): Promise<Sense[]> {
  const response = await request(`/api/reviews/${reviewId}/sense-research/regroup`, {
    method: 'POST', body: JSON.stringify({ modelId }),
  })
  return response.json()
}

export async function generateSenseDefinitions(reviewId: string, groups: DefinitionInput[], modelId: string): Promise<GeneratedDefinition[]> {
  const response = await request(`/api/reviews/${reviewId}/sense-research/definitions`, {
    method: 'POST', body: JSON.stringify({ groups, modelId }),
  })
  return response.json()
}

export async function getSenseModelOptions(): Promise<SenseModelOption[]> {
  const response = await request('/api/sense-models')
  return response.json()
}
