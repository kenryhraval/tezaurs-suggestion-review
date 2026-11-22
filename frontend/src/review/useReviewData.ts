import { useEffect, useState } from 'react'
import { getExamplesForReview, getSenses, getTezaursEntry, getTezaursFormOptions } from '../api/client'
import type { Example, Review, Sense, TezaursEntryCandidate, TezaursFormOptions } from '../api/types'

type SavedData = {
  selection: string
  senses: Sense[]
  examples: Example[]
  matchedEntry: TezaursEntryCandidate | null
  error: string
}

export function useReviewData(review: Review) {
  const selection = `${review.id}:${review.type}:${review.matchedEntryId}`
  const knownType = review.type !== 'UNKNOWN'
  const [saved, setSaved] = useState<SavedData | null>(null)
  const [formOptions, setFormOptions] = useState<TezaursFormOptions | null>(null)
  const [optionsError, setOptionsError] = useState('')
  const current = saved?.selection === selection ? saved : null

  useEffect(() => {
    if (!knownType) return
    let outdated = false
    async function loadSavedData() {
      try {
        const [senses, examples, matchedEntry] = await Promise.all([
          getSenses(review.id), getExamplesForReview(review.id),
          review.matchedEntryId === null ? Promise.resolve(null) : getTezaursEntry(review.matchedEntryId),
        ])
        if (!outdated) setSaved({ selection, senses, examples, matchedEntry, error: '' })
      } catch (cause) {
        if (!outdated) setSaved({ selection, senses: [], examples: [], matchedEntry: null,
          error: cause instanceof Error ? cause.message : 'Neizdevās ielādēt perēkļa datus.' })
      }
    }
    loadSavedData()
    return () => { outdated = true }
  }, [review.id, review.matchedEntryId, knownType, selection])

  useEffect(() => {
    if (!knownType) return
    let outdated = false
    async function loadFormOptions() {
      try {
        const options = await getTezaursFormOptions()
        if (!outdated) { setFormOptions(options); setOptionsError('') }
      } catch (cause) {
        if (!outdated) setOptionsError(cause instanceof Error ? cause.message : 'Neizdevās ielādēt formas izvēlnes.')
      }
    }
    loadFormOptions()
    return () => { outdated = true }
  }, [knownType])

  function saveSense(sense: Sense) {
    setSaved((data) => data?.selection === selection ? { ...data,
      senses: data.senses.some((item) => item.id === sense.id)
        ? data.senses.map((item) => item.id === sense.id ? sense : item) : [...data.senses, sense],
    } : data)
  }

  function replaceSenses(senses: Sense[]) {
    const assignments = new Map(senses.flatMap((sense) => sense.exampleIds.map((id) => [id, sense.id] as const)))
    setSaved((data) => data?.selection === selection ? { ...data, senses,
      examples: data.examples.map((example) => {
        const senseId = assignments.get(example.id) ?? null
        return { ...example, senseId, approved: example.senseId === senseId && example.approved }
      }),
    } : data)
  }

  function removeSense(senseId: string) {
    setSaved((data) => data?.selection === selection ? { ...data,
      senses: data.senses.filter((sense) => sense.id !== senseId),
      examples: data.examples.map((example) => example.senseId === senseId
        ? { ...example, senseId: null, approved: false } : example),
    } : data)
  }

  function saveExample(example: Example) {
    setSaved((data) => data?.selection === selection ? { ...data,
      examples: data.examples.some((item) => item.id === example.id)
        ? data.examples.map((item) => item.id === example.id ? example : item) : [...data.examples, example],
    } : data)
  }

  function replaceExamples(examples: Example[]) {
    setSaved((data) => data?.selection === selection ? { ...data, examples } : data)
  }

  function removeExample(exampleId: string) {
    setSaved((data) => data?.selection === selection ? { ...data,
      examples: data.examples.filter((example) => example.id !== exampleId),
    } : data)
  }

  const examples = current?.examples ?? []
  const exampleIdsBySense = new Map<string, string[]>()
  for (const example of examples) {
    if (example.senseId === null) continue
    const ids = exampleIdsBySense.get(example.senseId) ?? []
    ids.push(example.id)
    exampleIdsBySense.set(example.senseId, ids)
  }
  const senses = (current?.senses ?? []).map((sense) => ({ ...sense, exampleIds: exampleIdsBySense.get(sense.id) ?? [] }))

  return { senses, examples, matchedEntry: current?.matchedEntry ?? null,
    loading: knownType && current === null, error: current?.error ?? '', formOptions, optionsError,
    saveSense, replaceSenses, removeSense, saveExample, replaceExamples, removeExample }
}
