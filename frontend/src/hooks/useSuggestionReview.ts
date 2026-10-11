import { useEffect, useState } from 'react'
import { getReview } from '../api'
import type { Review, SuggestionStatus } from '../types'

export function useSuggestionReview(sourceSuggestionId: number, status: SuggestionStatus) {
  const [review, setReview] = useState<Review | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (status === 'NEW') return

    let active = true

    async function load() {
      try {
        const loaded = await getReview(sourceSuggestionId)
        if (active) {
          setReview(loaded)
          setError('')
        }
      } catch (cause) {
        if (active) {
          setError(cause instanceof Error ? cause.message : 'Pārskatu neizdevās ielādēt.')
        }
      }
    }

    void load()
    return () => {
      active = false
    }
  }, [sourceSuggestionId, status])

  return { review, setReview, error }
}
