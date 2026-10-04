import type {
  CorpusExample,
  Meaning,
  Review,
  Suggestion,
  SuggestionStatus,
  TezaursStatus,
} from './types'

async function request<T>(path: string, options?: RequestInit): Promise<T> {
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

  return response.json() as Promise<T>
}

export function getSuggestions() {
  return request<Suggestion[]>('/api/suggestions')
}

export function changeSuggestionStatus(id: number, status: SuggestionStatus) {
  return request<Suggestion>(`/api/suggestions/${id}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  })
}

export async function getReview(sourceSuggestionId: number) {
  const response = await fetch(`/api/reviews/by-source-suggestion/${sourceSuggestionId}`, {
    headers: { 'Content-Type': 'application/json' },
  })
  if (response.status === 204) return null
  if (!response.ok) throw new Error('Pārskatu neizdevās ielādēt.')
  return response.json() as Promise<Review>
}

export function correctReviewTerm(reviewId: string, reviewedTerm: string) {
  return request<Review>(`/api/reviews/${reviewId}/term-correction`, {
    method: 'POST',
    body: JSON.stringify({ reviewedTerm }),
  })
}

export function saveTezaursCheck(
  reviewId: string,
  status: TezaursStatus,
  matchedEntryId: number | null,
) {
  return request<Review>(`/api/reviews/${reviewId}/tezaurs-check`, {
    method: 'POST',
    body: JSON.stringify({ status, matchedEntryId }),
  })
}

export function getCorpusExamples(reviewId: string) {
  return request<CorpusExample[]>(`/api/reviews/${reviewId}/corpus-examples`)
}

export function addCorpusExample(reviewId: string, url: string) {
  return request<CorpusExample>(`/api/reviews/${reviewId}/corpus-examples`, {
    method: 'POST',
    body: JSON.stringify({ url }),
  })
}

export function getMeanings(reviewId: string) {
  return request<Meaning[]>(`/api/reviews/${reviewId}/meanings`)
}

export function reviseMeaning(reviewId: string, meaningId: string, gloss: string) {
  return request<Meaning>(
    `/api/reviews/${reviewId}/meanings/${meaningId}/revision`,
    { method: 'POST', body: JSON.stringify({ gloss }) },
  )
}
