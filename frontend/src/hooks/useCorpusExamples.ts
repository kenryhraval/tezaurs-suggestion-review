import { useEffect, useState } from 'react'
import { addCorpusExample, getCorpusExamples } from '../api'
import type { CorpusExample } from '../types'

export function useCorpusExamples(suggestionId: string, enabled: boolean) {
  const [examples, setExamples] = useState<CorpusExample[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!enabled) return
    let active = true

    async function load() {
      try {
        const loaded = await getCorpusExamples(suggestionId)
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
  }, [enabled, suggestionId])

  async function add(url: string) {
    setError('')
    try {
      const created = await addCorpusExample(suggestionId, url)
      setExamples((items) => [...items, created])
      return true
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās pievienot piemēru.')
      return false
    }
  }

  return { examples, loading, error, add }
}
