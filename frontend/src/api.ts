import type {
  CheckStatus,
  CreateSuggestionInput,
  Meaning,
  Suggestion,
  SuggestionStatus,
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

export function createSuggestion(input: CreateSuggestionInput) {
  return request<Suggestion>('/api/suggestions', {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function changeSuggestionStatus(id: string, status: SuggestionStatus) {
  return request<Suggestion>(`/api/suggestions/${id}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  })
}

export function correctSuggestionTerm(id: string, reviewedTerm: string) {
  return request<Suggestion>(`/api/suggestions/${id}/term-correction`, {
    method: 'POST',
    body: JSON.stringify({ reviewedTerm }),
  })
}

export function saveTezaursCheck(
  id: string,
  status: CheckStatus,
  matchedEntryId: number | null,
) {
  return request<Suggestion>(`/api/suggestions/${id}/tezaurs-check`, {
    method: 'POST',
    body: JSON.stringify({ status, matchedEntryId }),
  })
}

export function saveCorpusCheck(id: string, status: CheckStatus) {
  return request<Suggestion>(`/api/suggestions/${id}/corpus-check`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  })
}

export function getMeanings(suggestionId: string) {
  return request<Meaning[]>(`/api/suggestions/${suggestionId}/meanings`)
}

export function createMeaning(
  suggestionId: string,
  gloss: string,
) {
  return request<Meaning>(`/api/suggestions/${suggestionId}/meanings`, {
    method: 'POST',
    body: JSON.stringify({ gloss }),
  })
}

export function reviseMeaning(suggestionId: string, meaningId: string, gloss: string) {
  return request<Meaning>(
    `/api/suggestions/${suggestionId}/meanings/${meaningId}/revision`,
    { method: 'POST', body: JSON.stringify({ gloss }) },
  )
}

export function decideMeaning(
  suggestionId: string,
  meaningId: string,
  decision: 'approve' | 'reject',
) {
  return request<Meaning>(
    `/api/suggestions/${suggestionId}/meanings/${meaningId}/${decision}`,
    { method: 'POST' },
  )
}
