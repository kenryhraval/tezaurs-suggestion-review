import type {
  DuplicateReviewCandidate,
  EntryDraft,
  Review,
  ReviewListItem,
  QueuePage,
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
      items: result.items.map((item) => ({
        ...item,
        itemType: 'SUGGESTION' as const,
        status: 'NEW' as const,
        sourceCount: 1 as const,
      })),
    }
  }

  const result = await getReviews(status, query, page, size, sort)
  return {
    ...result,
    items: result.items.map((item) => ({ ...item, itemType: 'REVIEW' as const })),
  }
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
): Promise<DuplicateReviewCandidate[]> {
  const response = await request(
    `/api/reviews/duplicate-candidates?query=${encodeURIComponent(query)}`,
  )
  return response.json()
}

export async function linkDuplicateSuggestion(id: number, reviewId: string): Promise<void> {
  await request(`/api/suggestions/${id}/duplicate`, {
    method: 'POST',
    body: JSON.stringify({ reviewId }),
  })
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
  if (response.status === 204) return null
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
  const response = await request(
    `/api/reviews/${reviewId}/sense-drafts`,
    { method: 'PUT', body: JSON.stringify({ senses }) },
  )
  return response.json()
}
