import { useEffect, useState } from 'react'
import { getReview } from '../api'
import type { Review, SuggestionStatus } from '../types'

export function useSuggestionReview(sourceSuggestionId: number, status: SuggestionStatus) {
  const [review, setReview] = useState<Review | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true

    async function load() {
      try {
        const loaded = await getReview(sourceSuggestionId)
        if (active) setReview(loaded)
      } catch (cause) {
        if (active) {
          setError(cause instanceof Error ? cause.message : 'Pārskatu neizdevās ielādēt.')
        }
      } finally {
        if (active) setLoading(false)
      }
    }

    void load()
    return () => {
      active = false
    }
  }, [sourceSuggestionId, status])

  return { review, setReview, loading, error }
}
