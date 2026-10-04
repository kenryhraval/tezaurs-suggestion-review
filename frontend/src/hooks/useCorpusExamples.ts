import { useEffect, useState } from 'react'
import { addCorpusExample, getCorpusExamples } from '../api'
import type { CorpusExample } from '../types'

export function useCorpusExamples(reviewId: string | null, enabled: boolean) {
  const [examples, setExamples] = useState<CorpusExample[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!enabled || !reviewId) return
    const activeReviewId = reviewId
    let active = true

    async function load() {
      try {
        const loaded = await getCorpusExamples(activeReviewId)
        if (active) setExamples(loaded)
      } catch (cause) {
        if (active) {
          setError(cause instanceof Error ? cause.message : 'Neizdevās ielādēt piemērus.')
        }
      } finally {
        if (active) setLoading(false)
      }
    }

    void load()
    return () => {
      active = false
    }
  }, [enabled, reviewId])

  async function add(url: string) {
    setError('')
    try {
      if (!reviewId) return false
      const created = await addCorpusExample(reviewId, url)
      setExamples((items) => [...items, created])
      return true
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās pievienot piemēru.')
      return false
    }
  }

  return { examples, loading, error, add }
}
