import { useEffect, useState } from 'react'
import { getReview } from '../api'
import type { Review } from '../types'

export function useSuggestionReview(reviewId: string | null) {
  const [review, setReview] = useState<Review | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!reviewId) {
      return
    }
    const id = reviewId

    let active = true

    async function load() {
      try {
        const loaded = await getReview(id)
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
  }, [reviewId])

  return { review, setReview, error }
}
