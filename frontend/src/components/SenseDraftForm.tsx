import { useEffect, useState } from 'react'
import {
  getSenseDrafts,
  getTezaursEntry,
  getTezaursFormOptions,
  saveSenseDrafts,
} from '../api'
import type {
  Review,
  SaveSenseDraft,
  TezaursFormOptions,
  TezaursSense,
} from '../types'
import { FlagEditor } from './FlagEditor'
import { RestrictionEditor } from './RestrictionEditor'
import { DraftProperties } from './DraftProperties'

type Props = {
  reviewId: string
  initialGloss: string
  matchedEntryId: number | null
  onReviewUpdated: (review: Review) => void
}

function newSense(gloss: string): SaveSenseDraft {
  return {
    gloss,
    parentDraftSenseId: null,
    parentTezaursSenseId: null,
    flags: {},
    structuralRestrictions: null,
  }
}

export function SenseDraftForm({
  reviewId,
  initialGloss,
  matchedEntryId,
  onReviewUpdated,
}: Props) {
  const [senses, setSenses] = useState<SaveSenseDraft[]>([])
  const [existingSenses, setExistingSenses] = useState<TezaursSense[]>([])
  const [options, setOptions] = useState<TezaursFormOptions | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [savedSenses, setSavedSenses] = useState<SaveSenseDraft[]>([])
  const [editingIndex, setEditingIndex] = useState<number | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    const entryRequest = matchedEntryId === null
      ? Promise.resolve(null)
      : getTezaursEntry(matchedEntryId)

    Promise.all([getSenseDrafts(reviewId), getTezaursFormOptions(), entryRequest])
      .then(([loadedSenses, loadedOptions, entry]) => {
        if (!active) return
        setSenses(loadedSenses)
        setSavedSenses(loadedSenses)
        setOptions(loadedOptions)
        setExistingSenses(entry?.senses ?? [])
      })
      .catch((cause) => {
        if (active) {
          setError(cause instanceof Error ? cause.message : 'Neizdevās ielādēt nozīmju formu.')
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [matchedEntryId, reviewId])

  function replaceSense(index: number, updatedSense: SaveSenseDraft) {
    setSenses((currentSenses) => {
      const updatedSenses = [...currentSenses]
      updatedSenses[index] = updatedSense
      return updatedSenses
    })
  }

  function updateParent(index: number, value: string) {
    const sense = senses[index]
    if (!sense) return

    if (value.startsWith('draft:')) {
      replaceSense(index, {
        ...sense,
        parentDraftSenseId: value.slice('draft:'.length),
        parentTezaursSenseId: null,
      })
    } else if (value.startsWith('tezaurs:')) {
      replaceSense(index, {
        ...sense,
        parentDraftSenseId: null,
        parentTezaursSenseId: Number(value.slice('tezaurs:'.length)),
      })
    } else {
      replaceSense(index, {
        ...sense,
        parentDraftSenseId: null,
        parentTezaursSenseId: null,
      })
    }
  }

  function removeSense(index: number) {
    void save(senses.filter((_, itemIndex) => itemIndex !== index))
  }

  function addSense() {
    setEditingIndex(senses.length)
    setSenses((currentSenses) => [...currentSenses, newSense(senses.length === 0 ? initialGloss : '')])
  }

  async function save(nextSenses = senses) {
    setSaving(true)
    setError('')
    try {
      const response = await saveSenseDrafts(reviewId, nextSenses)
      setSenses(response.senses)
      setSavedSenses(response.senses)
      setEditingIndex(null)
      onReviewUpdated(response.review)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt nozīmju melnrakstu.')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <p className="muted">Ielādē nozīmju formu...</p>
  if (!options) return <p className="error">{error || 'Nozīmju forma nav pieejama.'}</p>

  return (
    <section className="sense-draft-section">
      <h3>Nozīmju melnraksts</h3>
      {senses.map((sense, index) => {
        const parentValue = sense.parentDraftSenseId
          ? `draft:${sense.parentDraftSenseId}`
          : sense.parentTezaursSenseId ? `tezaurs:${sense.parentTezaursSenseId}` : ''
        return (
          <fieldset className="sense-draft" key={sense.id ?? index}>
            <legend>{senses.length === 1 ? 'Jaunā nozīme' : `Jaunā nozīme ${index + 1}`}</legend>
            {editingIndex === index ? (
              <form onSubmit={(event) => {
                event.preventDefault()
                void save()
              }}>
                <label>
                  Skaidrojums
                  <textarea
                    onChange={(event) => replaceSense(index, {
                      ...sense,
                      gloss: event.target.value,
                    })}
                    required
                    rows={4}
                    value={sense.gloss}
                  />
                </label>
                {(existingSenses.length > 0 || senses.some((item) => item.id && item.id !== sense.id)) && (
                  <label>
                    Virsnozīme
                    <select value={parentValue} onChange={(event) => updateParent(index, event.target.value)}>
                      <option value="">Nav</option>
                      {existingSenses.map((item) => (
                        <option key={`tezaurs-${item.id}`} value={`tezaurs:${item.id}`}>
                          Esošā: {item.gloss || item.id}
                        </option>
                      ))}
                      {senses.filter((item) => item.id && item.id !== sense.id).map((item) => (
                        <option key={`draft-${item.id}`} value={`draft:${item.id}`}>
                          Melnraksta: {item.gloss}
                        </option>
                      ))}
                    </select>
                  </label>
                )}
                <FlagEditor
                  definitions={options.flags}
                  flags={sense.flags}
                  onChange={(flags) => replaceSense(index, { ...sense, flags })}
                  scope="S"
                />
                <RestrictionEditor
                  definitions={options.flags}
                  frequencies={options.restrictionFrequencies}
                  onChange={(structuralRestrictions) => replaceSense(index, {
                    ...sense,
                    structuralRestrictions,
                  })}
                  restriction={sense.structuralRestrictions}
                  types={options.restrictionTypes}
                />
                <button disabled={saving} type="submit">{saving ? 'Saglabā...' : 'Saglabāt nozīmi'}</button>
                <button disabled={saving} type="button" onClick={() => {
                  setSenses(savedSenses)
                  setEditingIndex(null)
                }}>Atcelt</button>
                {sense.id && senses.length > 1 && (
                  <button
                    type="button"
                    disabled={saving}
                    onClick={() => removeSense(index)}
                  >
                    Noņemt nozīmi
                  </button>
                )}
              </form>
            ) : (
              <>
                <p>{sense.gloss}</p>
                {parentValue && (
                  <p className="muted">Apakšnozīme:{' '}
                    {sense.parentDraftSenseId
                      ? senses.find((item) => item.id === sense.parentDraftSenseId)?.gloss
                      : existingSenses.find((item) => item.id === sense.parentTezaursSenseId)?.gloss}
                  </p>
                )}
                <DraftProperties flags={sense.flags} definitions={options.flags}
                  restriction={sense.structuralRestrictions} />
                <button disabled={editingIndex !== null || saving} type="button"
                  onClick={() => setEditingIndex(index)}>Labot nozīmi</button>
              </>
            )}
          </fieldset>
        )
      })}
      {senses.length === 0 && <p className="muted">Nozīmes vēl nav pievienotas.</p>}
      <button disabled={editingIndex !== null || saving} type="button" onClick={addSense}>
        Pievienot nozīmi
      </button>
      {error && <p className="error">{error}</p>}
    </section>
  )
}
