import { useEffect, useState, type FormEvent } from 'react'
import { createMeaning, decideMeaning, getMeanings, reviseMeaning } from '../api'
import { meaningOriginLabels } from '../labels'
import type { Meaning } from '../types'

type Props = {
  suggestionId: string
}

const meaningGroups = [
  { status: 'PROPOSED', label: 'Ieteiktās' },
  { status: 'APPROVED', label: 'Apstiprinātās' },
  { status: 'REJECTED', label: 'Noraidītās' },
] as const

function getMeaningHistory(meaning: Meaning, meaningsById: Map<string, Meaning>) {
  const history: Meaning[] = []
  const visited = new Set<string>()
  let previousId = meaning.supersedesMeaningId

  while (previousId && !visited.has(previousId)) {
    visited.add(previousId)
    const previous = meaningsById.get(previousId)
    if (!previous) break
    history.push(previous)
    previousId = previous.supersedesMeaningId
  }

  return history
}

export function MeaningSection({ suggestionId }: Props) {
  const [meanings, setMeanings] = useState<Meaning[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    async function loadMeanings() {
      try {
        setMeanings(await getMeanings(suggestionId))
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : 'Neizdevās ielādēt nozīmes.')
      } finally {
        setLoading(false)
      }
    }

    void loadMeanings()
  }, [suggestionId])

  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    const form = event.currentTarget
    const data = new FormData(form)

    try {
      const created = await createMeaning(suggestionId, String(data.get('gloss')))
      setMeanings((items) => [...items, created])
      form.reset()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās pievienot nozīmi.')
    }
  }

  async function handleRevision(meaningId: string, event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    const form = event.currentTarget
    const data = new FormData(form)

    try {
      const revision = await reviseMeaning(
        suggestionId,
        meaningId,
        String(data.get('gloss')),
      )
      setMeanings((items) => [
        ...items.map((item) =>
          item.id === meaningId ? { ...item, status: 'SUPERSEDED' as const } : item,
        ),
        revision,
      ])
      form.reset()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās labot nozīmi.')
    }
  }

  async function handleDecision(meaningId: string, decision: 'approve' | 'reject') {
    setError('')

    try {
      const updated = await decideMeaning(suggestionId, meaningId, decision)
      setMeanings((items) =>
        items.map((item) => (item.id === updated.id ? updated : item)),
      )
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās mainīt nozīmi.')
    }
  }

  const currentMeanings = meanings.filter((meaning) => meaning.status !== 'SUPERSEDED')
  const meaningsById = new Map(meanings.map((meaning) => [meaning.id, meaning]))

  return (
    <section className="meaning-section">
      <div className="meaning-heading">
        <h3>Nozīmes</h3>
        {!loading && <small>{currentMeanings.length}</small>}
      </div>

      {error && <p className="error">{error}</p>}
      {loading && <p>Ielādē nozīmes...</p>}
      {!loading && meanings.length === 0 && <p>Nozīmju nav.</p>}

      <div className="meaning-groups">
        {meaningGroups.map((group) => {
          const groupedMeanings = currentMeanings.filter(
            (meaning) => meaning.status === group.status,
          )
          if (groupedMeanings.length === 0) return null

          return (
            <section className="meaning-group" key={group.status}>
              <h4>
                {group.label} <small>{groupedMeanings.length}</small>
              </h4>
              <ul className="meanings">
                {groupedMeanings.map((meaning) => {
                  const history = getMeaningHistory(meaning, meaningsById)

                  return (
                    <li className="meaning-item" key={meaning.id}>
                      <div className="meaning-row">
                        <div className="meaning-copy">
                          <p>{meaning.gloss}</p>
                          <small>{meaningOriginLabels[meaning.origin]}</small>
                        </div>

                        <div className="meaning-actions">
                          <button
                            type="button"
                            disabled={meaning.status === 'APPROVED'}
                            onClick={() => void handleDecision(meaning.id, 'approve')}
                          >
                            Apstiprināt
                          </button>
                          <button
                            type="button"
                            disabled={meaning.status === 'REJECTED'}
                            onClick={() => void handleDecision(meaning.id, 'reject')}
                          >
                            Noraidīt
                          </button>

                          <details>
                            <summary>Labot</summary>
                            <form
                              onSubmit={(event) =>
                                void handleRevision(meaning.id, event)
                              }
                            >
                              <label>
                                <span className="visually-hidden">Labotā nozīme</span>
                                <textarea
                                  name="gloss"
                                  defaultValue={meaning.gloss}
                                  required
                                />
                              </label>
                              <button type="submit">Saglabāt labojumu</button>
                            </form>
                          </details>
                        </div>
                      </div>

                      {history.length > 0 && (
                        <details className="meaning-history">
                          <summary>Vēsture ({history.length})</summary>
                          <ul>
                            {history.map((version) => (
                              <li key={version.id}>
                                <p>{version.gloss}</p>
                                <small>{meaningOriginLabels[version.origin]}</small>
                              </li>
                            ))}
                          </ul>
                        </details>
                      )}
                    </li>
                  )
                })}
              </ul>
            </section>
          )
        })}
      </div>

      <details className="add-meaning">
        <summary>Pievienot nozīmi</summary>
        <form onSubmit={handleCreate}>
          <label>
            <span className="visually-hidden">Nozīme</span>
            <textarea
              name="gloss"
              maxLength={10000}
              placeholder="Nozīme"
              required
            />
          </label>
          <button type="submit">Pievienot</button>
        </form>
      </details>
    </section>
  )
}
