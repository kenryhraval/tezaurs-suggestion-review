import type {
  SenseModelOption,
  ModelOperation,
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
    const error = await response.json().catch(() => null)
    let message = typeof error?.message === 'string' && error.message.trim() ? error.message : ''
    if (!message) {
      switch (response.status) {
        case 400: message = 'Pārbaudiet ievadītos datus.'; break
        case 401:
        case 403: message = 'Lai veiktu šo darbību, jāpiesakās.'; break
        case 404: message = 'Ieraksts netika atrasts.'; break
        default: message = 'Pieprasījumu neizdevās izpildīt.'
      }
    }
    if (Array.isArray(error?.fieldErrors) && error.fieldErrors.length > 0) {
      const fields = error.fieldErrors.map((violation: { field: string }) => violation.field).join(', ')
      message += ` Neatbilstošie lauki: ${fields}.`
    }
    throw new Error(message)
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
  excludedReviewId: string,
): Promise<DuplicateReviewCandidate[]> {
  const parameters = new URLSearchParams({ query, excludedReviewId })
  const response = await request(`/api/reviews/duplicate-candidates?${parameters}`)
  return response.json()
}

export async function mergeReviews(sourceReviewId: string, reviewId: string): Promise<Review> {
  const response = await request(`/api/reviews/${sourceReviewId}/duplicate`, {
    method: 'POST',
    body: JSON.stringify({ reviewId }),
  })
  return response.json()
}

export async function getReviewByPublicId(publicId: string, signal?: AbortSignal): Promise<Review> {
  const response = await request(`/api/reviews/by-public-id/${publicId}`, { signal })
  return response.json()
}

export async function updateReviewTitle(reviewId: string, title: string): Promise<Review> {
  const response = await request(`/api/reviews/${reviewId}/term-correction`, {
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

export async function updateEntryDraft(
  reviewId: string,
  draft: SaveEntryDraft,
): Promise<EntryDraft> {
  const response = await request(
    `/api/reviews/${reviewId}/entry`,
    { method: 'PUT', body: JSON.stringify(draft) },
  )
  return response.json()
}

export async function createLexemeDraft(
  reviewId: string,
  lexeme: SaveLexemeDraft,
): Promise<LexemeDraft> {
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
): Promise<LexemeDraft> {
  const response = await request(
    `/api/reviews/${reviewId}/entry/lexemes/${lexemeId}`,
    { method: 'PUT', body: JSON.stringify(lexeme) },
  )
  return response.json()
}

export async function deleteLexemeDraft(
  reviewId: string,
  lexemeId: string,
): Promise<void> {
  await request(`/api/reviews/${reviewId}/entry/lexemes/${lexemeId}`, { method: 'DELETE' })
}

export async function getTezaursFormOptions(): Promise<TezaursFormOptions> {
  const response = await request('/api/tezaurs/form-options')
  return response.json()
}

export async function getTezaursEntry(entryId: number): Promise<TezaursEntryCandidate> {
  const response = await request(`/api/tezaurs/entries/${entryId}`)
  return response.json()
}

export async function getSenses(reviewId: string): Promise<Sense[]> {
  const response = await request(`/api/reviews/${reviewId}/senses`)
  return response.json()
}

export async function saveSense(reviewId: string, sense: SaveSense): Promise<Sense> {
  const path = `/api/reviews/${reviewId}/senses`
  const response = await request(sense.id ? `${path}/${sense.id}` : path, {
    method: sense.id ? 'PUT' : 'POST',
    body: JSON.stringify({
      gloss: sense.gloss,
      approved: sense.approved,
      parentSenseId: sense.parentSenseId,
      flags: sense.flags,
    }),
  })
  return response.json()
}

export async function mergeSenses(reviewId: string, sourceSenseId: string, targetSenseId: string): Promise<Sense> {
  const response = await request(`/api/reviews/${reviewId}/senses/${sourceSenseId}/merge`, {
    method: 'POST', body: JSON.stringify({ targetSenseId }),
  })
  return response.json()
}

export async function deleteSense(reviewId: string, senseId: string): Promise<void> {
  await request(`/api/reviews/${reviewId}/senses/${senseId}`, { method: 'DELETE' })
}

export async function updateExample(reviewId: string, exampleId: string, senseId: string | null, approved: boolean): Promise<Example> {
  const response = await request(`/api/reviews/${reviewId}/usage/examples/${exampleId}`, {
    method: 'PUT', body: JSON.stringify({ senseId, approved }),
  })
  return response.json()
}

export async function getExamplesForReview(reviewId: string): Promise<Example[]> {
  const response = await request(`/api/reviews/${reviewId}/usage/examples`)
  return response.json()
}

export async function saveExample(reviewId: string, example: SaveExample): Promise<Example> {
  const response = await request(`/api/reviews/${reviewId}/usage/examples`, {
    method: 'POST', body: JSON.stringify(example),
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

export async function regroupSenses(reviewId: string, modelId: string, additionalInstructions: string): Promise<Sense[]> {
  const response = await request(`/api/reviews/${reviewId}/sense-research/regroup`, {
    method: 'POST', body: JSON.stringify({ modelId, additionalInstructions }),
  })
  return response.json()
}

export async function generateSenseDefinitions(reviewId: string, modelId: string, additionalInstructions: string): Promise<Sense[]> {
  const response = await request(`/api/reviews/${reviewId}/sense-research/definitions`, {
    method: 'POST', body: JSON.stringify({ modelId, additionalInstructions }),
  })
  return response.json()
}

export async function getSenseModelOptions(): Promise<SenseModelOption[]> {
  const response = await request('/api/sense-models')
  return response.json()
}

export async function getModelPrompt(operation: ModelOperation): Promise<string> {
  const response = await request(`/api/sense-models/prompts/${operation}`)
  return response.text()
}
