import type {
  DuplicateReviewCandidate,
  EntryDraft,
  Review,
  ReviewListItem,
  QueuePage,
  QueueItem,
  SaveSenseDraft,
  SenseDraft,
  SuggestionPage,
  SuggestionSort,
  SuggestionStatus,
  SaveEntryDraft,
  SaveLexemeDraft,
  LexemeDraft,
  TezaursEntryCandidate,
  TezaursEntryDetails,
  TezaursFormOptions,
  TezaursStatus,
  UsageDecision,
  UsageEvidence,
  SaveUsageEvidence,
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
    if (response.status === 409) {
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

export async function getSuggestions(
  query: string,
  page: number,
  size: number,
  sort: SuggestionSort,
): Promise<SuggestionPage> {
  const parameters = new URLSearchParams({
    query,
    page: String(page),
    size: String(size),
    sort,
  })
  const response = await request(`/api/suggestions?${parameters}`)
  return response.json()
}

async function getReviews(
  status: SuggestionStatus,
  query: string,
  page: number,
  size: number,
  sort: SuggestionSort,
): Promise<{ items: ReviewListItem[]; page: number; totalPages: number; totalItems: number }> {
  const parameters = new URLSearchParams({
    status,
    query,
    page: String(page),
    size: String(size),
    sort,
  })
  const response = await request(`/api/reviews?${parameters}`)
  return response.json()
}

export async function getQueueItems(
  status: SuggestionStatus,
  query: string,
  page: number,
  size: number,
  sort: SuggestionSort,
): Promise<QueuePage> {
  if (status === 'NEW') {
    const result = await getSuggestions(query, page, size, sort)
    return {
      ...result,
      items: result.items.map((item): QueueItem => ({
        ...item,
        itemType: 'SUGGESTION',
        status: 'NEW',
        sourceCount: 1,
      })),
    }
  }

  const result = await getReviews(status, query, page, size, sort)
  return {
    ...result,
    items: result.items.map((item): QueueItem => ({ ...item, itemType: 'REVIEW' })),
  }
}

export async function detachSuggestion(reviewId: string, sourceId: number): Promise<Review> {
  const response = await request(`/api/reviews/${reviewId}/suggestions/${sourceId}`, {
    method: 'DELETE',
  })
  return response.json()
}

export async function changeSuggestionStatus(
  id: number,
  status: SuggestionStatus,
): Promise<Review> {
  const response = await request(`/api/suggestions/${id}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  })
  return response.json()
}

export async function changeReviewStatus(
  id: string,
  status: SuggestionStatus,
): Promise<Review> {
  const response = await request(`/api/reviews/${id}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  })
  return response.json()
}

export async function getDuplicateReviewCandidates(
  query: string,
  excludedCaseId?: string,
): Promise<DuplicateReviewCandidate[]> {
  const parameters = new URLSearchParams({ query })
  if (excludedCaseId) {
    parameters.set('excludedCaseId', excludedCaseId)
  }
  const response = await request(`/api/reviews/duplicate-candidates?${parameters}`)
  return response.json()
}

export async function linkDuplicateSuggestion(id: number, reviewId: string): Promise<Review> {
  const response = await request(`/api/suggestions/${id}/duplicate`, {
    method: 'POST',
    body: JSON.stringify({ reviewId }),
  })
  return response.json()
}

export async function getReview(reviewId: string): Promise<Review> {
  const response = await request(`/api/reviews/${reviewId}`)
  return response.json()
}

export async function correctReviewTerm(
  reviewId: string,
  reviewedTerm: string,
): Promise<Review> {
  const response = await request(`/api/reviews/${reviewId}/term-correction`, {
    method: 'POST',
    body: JSON.stringify({ reviewedTerm }),
  })
  return response.json()
}

export async function saveTezaursCheck(
  reviewId: string,
  status: TezaursStatus,
  matchedEntryId: number | null,
): Promise<Review> {
  const response = await request(`/api/reviews/${reviewId}/tezaurs-check`, {
    method: 'POST',
    body: JSON.stringify({ status, matchedEntryId }),
  })
  return response.json()
}

export async function getTezaursCandidates(
  reviewId: string,
  query?: string,
): Promise<TezaursEntryCandidate[]> {
  const search = query ? `?query=${encodeURIComponent(query)}` : ''
  const response = await request(`/api/reviews/${reviewId}/tezaurs-candidates${search}`)
  return response.json()
}

export async function getEntryDraft(reviewId: string): Promise<EntryDraft | null> {
  const response = await request(`/api/reviews/${reviewId}/entry-draft`)
  if (response.status === 204) {
    return null
  }
  return response.json()
}

export async function createEntryDraft(
  reviewId: string,
): Promise<{ draft: EntryDraft; review: Review }> {
  const response = await request(
    `/api/reviews/${reviewId}/entry-draft`,
    { method: 'POST' },
  )
  return response.json()
}

export async function updateEntryDraft(
  reviewId: string,
  draft: SaveEntryDraft,
): Promise<{ draft: EntryDraft; review: Review }> {
  const response = await request(
    `/api/reviews/${reviewId}/entry-draft`,
    { method: 'PUT', body: JSON.stringify(draft) },
  )
  return response.json()
}

export async function createLexemeDraft(
  reviewId: string,
  lexeme: SaveLexemeDraft,
): Promise<{ lexeme: LexemeDraft; review: Review }> {
  const response = await request(
    `/api/reviews/${reviewId}/entry-draft/lexemes`,
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
    `/api/reviews/${reviewId}/entry-draft/lexemes/${lexemeId}`,
    { method: 'PUT', body: JSON.stringify(lexeme) },
  )
  return response.json()
}

export async function deleteLexemeDraft(
  reviewId: string,
  lexemeId: string,
): Promise<Review> {
  const response = await request(
    `/api/reviews/${reviewId}/entry-draft/lexemes/${lexemeId}`,
    { method: 'DELETE' },
  )
  return response.json()
}

export async function getTezaursFormOptions(): Promise<TezaursFormOptions> {
  const response = await request('/api/tezaurs/form-options')
  return response.json()
}

export async function getTezaursEntry(entryId: number): Promise<TezaursEntryDetails> {
  const response = await request(`/api/tezaurs/entries/${entryId}`)
  return response.json()
}

export async function getSenseDrafts(reviewId: string): Promise<SenseDraft[]> {
  const response = await request(`/api/reviews/${reviewId}/sense-drafts`)
  return response.json()
}

export async function saveSenseDrafts(
  reviewId: string,
  senses: SaveSenseDraft[],
): Promise<{ senses: SenseDraft[]; review: Review }> {
  const requestSenses = senses.map((sense) => ({
    id: sense.id,
    sourceSenseId: sense.sourceSenseId,
    gloss: sense.gloss,
    parentDraftSenseId: sense.parentDraftSenseId,
    parentTezaursSenseId: sense.parentTezaursSenseId,
    flags: sense.flags,
    structuralRestrictions: sense.structuralRestrictions,
  }))
  const response = await request(
    `/api/reviews/${reviewId}/sense-drafts`,
    { method: 'PUT', body: JSON.stringify({ senses: requestSenses }) },
  )
  return response.json()
}

export async function getUsageEvidence(reviewId: string): Promise<UsageEvidence[]> {
  const response = await request(`/api/reviews/${reviewId}/usage/evidence`)
  return response.json()
}

export async function saveUsageEvidence(reviewId: string, evidence: SaveUsageEvidence): Promise<UsageEvidence> {
  const response = await request(`/api/reviews/${reviewId}/usage/evidence`, {
    method: 'POST', body: JSON.stringify(evidence),
  })
  return response.json()
}

export async function deleteUsageEvidence(reviewId: string, evidenceId: string): Promise<void> {
  await request(`/api/reviews/${reviewId}/usage/evidence/${evidenceId}`, { method: 'DELETE' })
}

export async function saveUsageDecision(reviewId: string, decision: UsageDecision): Promise<Review> {
  const response = await request(`/api/reviews/${reviewId}/usage/decision`, {
    method: 'POST', body: JSON.stringify({ decision }),
  })
  return response.json()
}

export async function searchCorpus(
  reviewId: string, query: string, corpus: string, page: number,
): Promise<CorpusSearchResult> {
  const parameters = new URLSearchParams({ query, corpus, page: String(page) })
  const response = await request(`/api/reviews/${reviewId}/usage/search?${parameters}`)
  return response.json()
}

export async function getCorpora(): Promise<CorpusOption[]> {
  const response = await request('/api/corpora')
  return response.json()
}
