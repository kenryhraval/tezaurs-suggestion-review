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
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    const entryRequest = matchedEntryId === null
      ? Promise.resolve(null)
      : getTezaursEntry(matchedEntryId)

    Promise.all([getSenseDrafts(reviewId), getTezaursFormOptions(), entryRequest])
      .then(([loadedSenses, loadedOptions, entry]) => {
        if (!active) return
        setSenses(loadedSenses.length > 0 ? loadedSenses : [newSense(initialGloss)])
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
  }, [initialGloss, matchedEntryId, reviewId])

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
    setSenses((currentSenses) => currentSenses.filter((_, itemIndex) => itemIndex !== index))
  }

  function addSense() {
    setSenses((currentSenses) => [...currentSenses, newSense('')])
  }

  async function save() {
    setSaving(true)
    setSaved(false)
    setError('')
    try {
      const response = await saveSenseDrafts(reviewId, senses)
      setSenses(response.senses)
      onReviewUpdated(response.review)
      setSaved(true)
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
            {senses.length > 1 && (
              <button
                type="button"
                onClick={() => removeSense(index)}
              >
                Noņemt nozīmi
              </button>
            )}
          </fieldset>
        )
      })}
      <button type="button" onClick={addSense}>
        Pievienot nozīmi
      </button>{' '}
      <button disabled={saving || senses.some((sense) => !sense.gloss.trim())} type="button" onClick={save}>
        {saving ? 'Saglabā...' : 'Saglabāt nozīmju melnrakstu'}
      </button>
      {error && <p className="error">{error}</p>}
      {saved && <p>Nozīmju melnraksts saglabāts.</p>}
    </section>
  )
}
