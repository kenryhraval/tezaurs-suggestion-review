import { useEffect, useState, type ReactNode } from 'react'
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
  TezaursEntryDetails,
  FlagMap,
} from '../types'
import { FlagEditor } from './FlagEditor'
import { RestrictionEditor } from './RestrictionEditor'
import { SenseDetails } from './ExistingEntryDetails'
import { EditDialog } from './EditDialog'
import { SenseActions } from './TezaursActions'
import { ConfirmationDialog } from './ConfirmationDialog'
import { draftGrammarData } from '../draftEntryView'

type Props = {
  reviewId: string
  initialGloss: string
  matchedEntryId: number | null
  editExistingSenses: boolean
  editable: boolean
  onReviewUpdated: (review: Review) => void
  renderEntry: (senses: ReactNode, onAddSense?: () => void) => ReactNode
}

type DisplayedSense = {
  key: string
  parentKey: string | null
  draftIndex: number | null
  sense: TezaursEntryDetails['senses'][number]
  isNew: boolean
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
  editExistingSenses,
  editable,
  onReviewUpdated,
  renderEntry,
}: Props) {
  const [senses, setSenses] = useState<SaveSenseDraft[]>([])
  const [existingEntry, setExistingEntry] = useState<TezaursEntryDetails | null>(null)
  const [options, setOptions] = useState<TezaursFormOptions | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [savedSenses, setSavedSenses] = useState<SaveSenseDraft[]>([])
  const [editingIndex, setEditingIndex] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [editingParent, setEditingParent] = useState(false)
  const [deletingIndex, setDeletingIndex] = useState<number | null>(null)

  useEffect(() => {
    let active = true
    const entryRequest = matchedEntryId === null
      ? Promise.resolve(null)
      : getTezaursEntry(matchedEntryId)

    Promise.all([getSenseDrafts(reviewId), getTezaursFormOptions(), entryRequest])
      .then(([loadedSenses, loadedOptions, entry]) => {
        if (!active) return
        const editable = editExistingSenses ? loadedSenses : loadedSenses.filter((sense) => !sense.sourceSenseId)
        if (!editExistingSenses) {
          editable.forEach((sense) => {
            const parent = loadedSenses.find((item) => item.id === sense.parentDraftSenseId)
            if (parent?.sourceSenseId) {
              sense.parentDraftSenseId = null
              sense.parentTezaursSenseId = parent.sourceSenseId
            }
          })
        }
        setSenses(editable)
        setSavedSenses(editable)
        setOptions(loadedOptions)
        setExistingEntry(entry)
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
  }, [matchedEntryId, reviewId, editExistingSenses])

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

  function addSense(parentDraftSenseId: string | null = null, parentTezaursSenseId: number | null = null) {
    setError('')
    setEditingParent(false)
    setEditingIndex(senses.length)
    const sense = newSense(senses.length === 0 ? initialGloss : '')
    sense.parentDraftSenseId = parentDraftSenseId
    sense.parentTezaursSenseId = parentTezaursSenseId
    setSenses((currentSenses) => [...currentSenses, sense])
  }

  function editSense(index: number, parentOnly = false, promote = false) {
    setError('')
    setEditingParent(parentOnly)
    setEditingIndex(index)
    if (promote) {
      replaceSense(index, { ...senses[index], parentDraftSenseId: null, parentTezaursSenseId: null })
    }
  }

  function editOriginalSense(source: TezaursEntryDetails['senses'][number], parentOnly = false, promote = false) {
    const gram = source.data?.Gram as {
      Flags?: FlagMap
      StructuralRestrictions?: Record<string, unknown> | Record<string, unknown>[]
    } | undefined
    const restrictions = gram?.StructuralRestrictions
    const draft: SaveSenseDraft = {
      sourceSenseId: source.id,
      gloss: source.gloss ?? '',
      parentDraftSenseId: null,
      parentTezaursSenseId: promote ? null : source.parentSenseId,
      flags: gram?.Flags ?? {},
      structuralRestrictions: Array.isArray(restrictions) ? { AND: restrictions } : restrictions ?? null,
    }
    setError('')
    setEditingParent(parentOnly)
    setEditingIndex(senses.length)
    setSenses((current) => [...current, draft])
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
      return true
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt nozīmju melnrakstu.')
      return false
    } finally {
      setSaving(false)
    }
  }

  if (loading) return renderEntry(<p className="muted">Ielādē nozīmju formu...</p>)
  if (!options) return renderEntry(<p className="error">{error || 'Nozīmju forma nav pieejama.'}</p>)

  const existingSenses = existingEntry?.senses ?? []
  const displayed: DisplayedSense[] = existingSenses
    .filter((sense) => !senses.some((draft) => draft.id && draft.sourceSenseId === sense.id))
    .map((sense) => {
      const parent = sense.parentSenseId === null ? undefined
        : senses.find((draft) => draft.id && draft.sourceSenseId === sense.parentSenseId)
      let parentKey = sense.parentSenseId === null ? null : `tezaurs:${sense.parentSenseId}`
      if (parent) parentKey = `draft:${parent.id}`
      return { key: `tezaurs:${sense.id}`, parentKey, draftIndex: null, sense, isNew: false }
    })
  senses.forEach((sense, index) => {
    if (!sense.id) return
    const original = existingSenses.find((item) => item.id === sense.sourceSenseId)
    const parentDraft = senses.find((item) => item.sourceSenseId === sense.parentTezaursSenseId)
    let parentKey = sense.parentDraftSenseId ? `draft:${sense.parentDraftSenseId}` : null
    if (sense.parentTezaursSenseId) {
      parentKey = parentDraft ? `draft:${parentDraft.id}` : `tezaurs:${sense.parentTezaursSenseId}`
    }
    displayed.push({ key: `draft:${sense.id}`, parentKey, draftIndex: index,
      isNew: existingEntry !== null && !sense.sourceSenseId,
      sense: { id: sense.sourceSenseId ?? index, parentSenseId: null, orderNo: index, senseTag: null,
        gloss: sense.gloss, hidden: false, examples: original?.examples ?? [],
        data: draftGrammarData(original?.data, sense.flags, sense.structuralRestrictions) } })
  })
  const originalOrder = new Map(existingSenses.map((sense, index) => [sense.id, index]))
  displayed.sort((left, right) => {
    if (left.isNew !== right.isNew) return left.isNew ? 1 : -1
    if (left.isNew) return 0
    return (originalOrder.get(left.sense.id) ?? 0) - (originalOrder.get(right.sense.id) ?? 0)
  })
  displayed.forEach((item) => {
    if (item.parentKey && !displayed.some((parent) => parent.key === item.parentKey)) item.parentKey = null
  })

  function renderSenses(parentKey: string | null, prefix = '') {
    const siblings = displayed.filter((item) => item.parentKey === parentKey)
    return siblings.map((item, index) => {
      const number = prefix ? `${prefix}.${index + 1}` : String(index + 1)
      const showNumber = prefix !== '' || siblings.length > 1 || displayed.some((child) => child.parentKey === item.key)
      const draftIndex = item.draftIndex
      const canEdit = editable && (draftIndex !== null || editExistingSenses)
      const hasChildren = displayed.some((child) => child.parentKey === item.key)
      function openEditor(parentOnly = false, promote = false) {
        if (draftIndex !== null) editSense(draftIndex, parentOnly, promote)
        else editOriginalSense(item.sense, parentOnly, promote)
      }
      return (
        <div key={item.key}>
          <SenseDetails number={showNumber ? number : ''} sense={item.sense} isNew={item.isNew}
            actions={
              <SenseActions subsense={parentKey !== null}
                onEdit={canEdit ? () => openEditor() : undefined}
                onAddSubsense={editable ? () => {
                  if (draftIndex !== null) addSense(senses[draftIndex].id ?? null)
                  else addSense(null, item.sense.id)
                } : undefined}
                onMove={canEdit && displayed.length > 1 ? () => openEditor(true) : undefined}
                onPromote={canEdit ? () => openEditor(true, true) : undefined}
                onDelete={editable && draftIndex !== null && senses.length > 1 && !hasChildren ? () => {
                  setError('')
                  setDeletingIndex(draftIndex)
                } : undefined}
              />
            } />
          <div className="existing-subsenses">{renderSenses(item.key, number)}</div>
        </div>
      )
    })
  }

  const content = (
    <section aria-label="Nozīmes">
      {renderSenses(null)}
      {senses.map((sense, index) => {
        const parentValue = sense.parentDraftSenseId
          ? `draft:${sense.parentDraftSenseId}`
          : sense.parentTezaursSenseId ? `tezaurs:${sense.parentTezaursSenseId}` : ''
        let editorTitle = sense.id || sense.sourceSenseId ? 'Labot nozīmi' : 'Pievienot nozīmi'
        if (editingParent) editorTitle = 'Pārvietot nozīmi'
        return (
          <div key={sense.id ?? index}>
            {editingIndex === index && (
              <EditDialog title={editorTitle} busy={saving}
                error={error} onCancel={() => {
                  setSenses(savedSenses)
                  setEditingIndex(null)
                  setError('')
                }} onSubmit={() => {
                  void save()
                }}>
                {!editingParent && <label>
                  Skaidrojums
                  <textarea
                    autoFocus
                    onChange={(event) => replaceSense(index, {
                      ...sense,
                      gloss: event.target.value,
                    })}
                    required
                    rows={4}
                    value={sense.gloss}
                  />
                </label>}
                {(existingSenses.length > 0 || senses.some((item) => item.id && item.id !== sense.id)) && (
                  <label>
                    Virsnozīme
                    <select value={parentValue} onChange={(event) => updateParent(index, event.target.value)}>
                      <option value="">Nav</option>
                      {existingSenses.filter((item) => !senses.some((draft) => draft.sourceSenseId === item.id)).map((item) => (
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
                {!editingParent && <>
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
                </>}
              </EditDialog>
            )}
          </div>
        )
      })}
      {displayed.length === 0 && <p className="muted">Nozīmes vēl nav pievienotas.</p>}
      {deletingIndex !== null && (
        <ConfirmationDialog message={`Dzēst nozīmi “${senses[deletingIndex]?.gloss}” no melnraksta?`}
          busy={saving} error={error}
          onConfirm={async () => {
            if (await save(senses.filter((_, index) => index !== deletingIndex))) setDeletingIndex(null)
          }}
          onCancel={() => { setDeletingIndex(null); setError('') }} />
      )}
      {error && editingIndex === null && deletingIndex === null && <p className="error">{error}</p>}
    </section>
  )
  return renderEntry(content, editable ? () => addSense() : undefined)
}
