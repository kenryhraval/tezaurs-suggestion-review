import { useEffect, useState } from 'react'
import { getMeanings, reviseMeaning } from '../api'
import type { Meaning } from '../types'

function getHistory(current: Meaning | undefined, meanings: Meaning[]) {
  if (!current) return []

  const byId = new Map(meanings.map((meaning) => [meaning.id, meaning]))
  const history: Meaning[] = []
  const visited = new Set<string>()
  let previousId = current.supersedesMeaningId

  while (previousId && !visited.has(previousId)) {
    visited.add(previousId)
    const previous = byId.get(previousId)
    if (!previous) break
    history.push(previous)
    previousId = previous.supersedesMeaningId
  }

  return history
}

export function useSuggestionMeaning(suggestionId: string) {
  const [meanings, setMeanings] = useState<Meaning[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true

    async function load() {
      try {
        const loaded = await getMeanings(suggestionId)
        if (active) setMeanings(loaded)
      } catch (cause) {
        if (active) {
          setError(cause instanceof Error ? cause.message : 'Neizdevās ielādēt nozīmi.')
        }
      } finally {
        if (active) setLoading(false)
      }
    }

    void load()
    return () => {
      active = false
    }
  }, [suggestionId])

  const submitted = meanings.find((meaning) => meaning.origin === 'SUBMITTER')
  const current = meanings.find((meaning) => meaning.status === 'CURRENT')

  async function revise(gloss: string) {
    if (!current) return

    setError('')
    try {
      const revision = await reviseMeaning(suggestionId, current.id, gloss)
      setMeanings((items) => [
        ...items.map((item) => item.id === current.id
          ? { ...item, status: 'SUPERSEDED' as const }
          : item),
        revision,
      ])
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās labot nozīmi.')
    }
  }

  return {
    submitted,
    current,
    history: getHistory(current, meanings),
    loading,
    error,
    revise,
  }
}
