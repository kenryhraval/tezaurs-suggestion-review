import { useEffect, useState, type ReactNode } from 'react'
import {
  getSenseDrafts,
  getTezaursEntry,
  getTezaursFormOptions,
  saveSenseDrafts,
} from '../api/client'
import type {
  Review,
  SaveSenseDraft,
  TezaursFormOptions,
  TezaursEntryDetails,
  TezaursSenseDetails,
  FlagMap,
} from '../api/types'
import { FlagEditor } from './FlagEditor'
import { RestrictionEditor } from './RestrictionEditor'
import { SenseDetails } from './ExistingEntryDetails'
import { EditDialog } from './EditDialog'
import { SenseActions } from './TezaursActions'
import { ConfirmationDialog } from './ConfirmationDialog'
import { draftGrammarData } from '../drafts/grammar'

type Props = {
  reviewId: string
  initialGloss: string
  matchedEntryId: number | null
  editExistingSenses: boolean
  editable: boolean
  onReviewUpdated: (review: Review) => void
  renderEntry: (senses: ReactNode, onAddSense?: () => void) => ReactNode
}

type SenseEditAction = 'edit' | 'move' | 'promote'

type DisplayedSense = {
  key: string
  parentKey: string | null
  draftIndex: number | null
  sense: TezaursSenseDetails
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

function buildDisplayedSenses(existingEntry: TezaursEntryDetails | null, draftSenses: SaveSenseDraft[]) {
  const existingSenses = existingEntry?.senses ?? []
  const displayedSenses: DisplayedSense[] = []

  for (const sense of existingSenses) {
    const savedDraft = draftSenses.find((draft) => draft.id && draft.sourceSenseId === sense.id)
    if (savedDraft) {
      continue
    }

    let parentKey: string | null = null
    if (sense.parentSenseId !== null) {
      const parentDraft = draftSenses.find((draft) => draft.id && draft.sourceSenseId === sense.parentSenseId)
      if (parentDraft) {
        parentKey = `draft:${parentDraft.id}`
      } else {
        parentKey = `tezaurs:${sense.parentSenseId}`
      }
    }
    displayedSenses.push({
      key: `tezaurs:${sense.id}`,
      parentKey,
      draftIndex: null,
      sense,
      isNew: false,
    })
  }

  draftSenses.forEach((draft, index) => {
    if (!draft.id) {
      return
    }

    const originalSense = existingSenses.find((sense) => sense.id === draft.sourceSenseId)
    let parentKey = draft.parentDraftSenseId ? `draft:${draft.parentDraftSenseId}` : null
    if (draft.parentTezaursSenseId) {
      const parentDraft = draftSenses.find((sense) => sense.sourceSenseId === draft.parentTezaursSenseId)
      if (parentDraft) {
        parentKey = `draft:${parentDraft.id}`
      } else {
        parentKey = `tezaurs:${draft.parentTezaursSenseId}`
      }
    }

    displayedSenses.push({
      key: `draft:${draft.id}`,
      parentKey,
      draftIndex: index,
      isNew: existingEntry !== null && !draft.sourceSenseId,
      sense: {
        id: draft.sourceSenseId ?? index,
        parentSenseId: null,
        orderNo: index,
        senseTag: null,
        gloss: draft.gloss,
        hidden: false,
        examples: originalSense?.examples ?? [],
        data: draftGrammarData(originalSense?.data, draft.flags, draft.structuralRestrictions),
      },
    })
  })

  const originalSenseOrder = new Map(existingSenses.map((sense, index) => [sense.id, index]))
  displayedSenses.sort((left, right) => {
    if (left.isNew !== right.isNew) {
      return left.isNew ? 1 : -1
    }
    if (left.isNew) {
      return 0
    }

    const leftPosition = originalSenseOrder.get(left.sense.id) ?? 0
    const rightPosition = originalSenseOrder.get(right.sense.id) ?? 0
    return leftPosition - rightPosition
  })

  for (const sense of displayedSenses) {
    const parentIsVisible = displayedSenses.some((parent) => parent.key === sense.parentKey)
    if (sense.parentKey && !parentIsVisible) {
      sense.parentKey = null
    }
  }
  return displayedSenses
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
    let requestIsOutdated = false
    const entryRequest = matchedEntryId === null
      ? Promise.resolve(null)
      : getTezaursEntry(matchedEntryId)

    async function loadSenseForm() {
      try {
        const [loadedSenses, loadedOptions, entry] = await Promise.all([
          getSenseDrafts(reviewId),
          getTezaursFormOptions(),
          entryRequest,
        ])
        if (requestIsOutdated) {
          return
        }

        let editableSenses = loadedSenses
        if (!editExistingSenses) {
          editableSenses = loadedSenses.filter((sense) => !sense.sourceSenseId)
          for (const sense of editableSenses) {
            const parent = loadedSenses.find((item) => item.id === sense.parentDraftSenseId)
            if (parent?.sourceSenseId) {
              sense.parentDraftSenseId = null
              sense.parentTezaursSenseId = parent.sourceSenseId
            }
          }
        }
        setSenses(editableSenses)
        setSavedSenses(editableSenses)
        setOptions(loadedOptions)
        setExistingEntry(entry)
      } catch (cause) {
        if (!requestIsOutdated) {
          setError(cause instanceof Error ? cause.message : 'Neizdevās ielādēt nozīmju formu.')
        }
      } finally {
        if (!requestIsOutdated) {
          setLoading(false)
        }
      }
    }

    loadSenseForm()

    return () => {
      requestIsOutdated = true
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
    if (!sense) {
      return
    }

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

  function editSense(index: number, action: SenseEditAction) {
    setError('')
    setEditingParent(action !== 'edit')
    setEditingIndex(index)
    if (action === 'promote') {
      replaceSense(index, { ...senses[index], parentDraftSenseId: null, parentTezaursSenseId: null })
    }
  }

  function editOriginalSense(source: TezaursSenseDetails, action: SenseEditAction) {
    const gram = source.data?.Gram as {
      Flags?: FlagMap
      StructuralRestrictions?: Record<string, unknown> | Record<string, unknown>[]
    } | undefined
    const restrictions = gram?.StructuralRestrictions
    const draft: SaveSenseDraft = {
      sourceSenseId: source.id,
      gloss: source.gloss ?? '',
      parentDraftSenseId: null,
      parentTezaursSenseId: action === 'promote' ? null : source.parentSenseId,
      flags: gram?.Flags ?? {},
      structuralRestrictions: Array.isArray(restrictions) ? { AND: restrictions } : restrictions ?? null,
    }
    setError('')
    setEditingParent(action !== 'edit')
    setEditingIndex(senses.length)
    setSenses((current) => [...current, draft])
  }

  async function saveSenses(nextSenses = senses) {
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

  function cancelSenseEdit() {
    setSenses(savedSenses)
    setEditingIndex(null)
    setError('')
  }

  function cancelSenseDeletion() {
    setDeletingIndex(null)
    setError('')
  }

  async function confirmSenseDeletion() {
    if (deletingIndex === null) {
      return
    }
    const remainingSenses = senses.filter((_, index) => index !== deletingIndex)
    const saved = await saveSenses(remainingSenses)
    if (saved) {
      setDeletingIndex(null)
    }
  }

  if (loading) {
    return renderEntry(<p className="muted">Ielādē nozīmju formu...</p>)
  }
  if (!options) {
    return renderEntry(<p className="error">{error || 'Nozīmju forma nav pieejama.'}</p>)
  }

  const existingSenses = existingEntry?.senses ?? []
  const displayedSenses = buildDisplayedSenses(existingEntry, senses)

  function renderSenses(parentKey: string | null, prefix = '') {
    const siblings = displayedSenses.filter((item) => item.parentKey === parentKey)
    return siblings.map((item, index) => {
      const number = prefix ? `${prefix}.${index + 1}` : String(index + 1)
      const hasChildren = displayedSenses.some((child) => child.parentKey === item.key)
      const showNumber = prefix !== '' || siblings.length > 1 || hasChildren
      const draftIndex = item.draftIndex
      const canEdit = editable && (draftIndex !== null || editExistingSenses)
      const canDelete = editable && draftIndex !== null && senses.length > 1 && !hasChildren
      function openEditor(action: SenseEditAction) {
        if (draftIndex !== null) {
          editSense(draftIndex, action)
        } else {
          editOriginalSense(item.sense, action)
        }
      }
      return (
        <div key={item.key}>
          <SenseDetails number={showNumber ? number : ''} sense={item.sense} isNew={item.isNew}
            actions={
              <SenseActions subsense={parentKey !== null}
                onEdit={canEdit ? () => openEditor('edit') : undefined}
                onAddSubsense={editable ? () => {
                  if (draftIndex !== null) {
                    addSense(senses[draftIndex].id ?? null)
                  } else {
                    addSense(null, item.sense.id)
                  }
                } : undefined}
                onMove={canEdit && displayedSenses.length > 1 ? () => openEditor('move') : undefined}
                onPromote={canEdit ? () => openEditor('promote') : undefined}
                onDelete={canDelete ? () => {
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
        let parentValue = ''
        if (sense.parentDraftSenseId) {
          parentValue = `draft:${sense.parentDraftSenseId}`
        } else if (sense.parentTezaursSenseId) {
          parentValue = `tezaurs:${sense.parentTezaursSenseId}`
        }
        let editorTitle = sense.id || sense.sourceSenseId ? 'Labot nozīmi' : 'Pievienot nozīmi'
        if (editingParent) {
          editorTitle = 'Pārvietot nozīmi'
        }
        return (
          <div key={sense.id ?? index}>
            {editingIndex === index && (
              <EditDialog title={editorTitle} busy={saving}
                error={error} onCancel={cancelSenseEdit} onSubmit={() => saveSenses()}>
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
      {displayedSenses.length === 0 && <p className="muted">Nozīmes vēl nav pievienotas.</p>}
      {deletingIndex !== null && (
        <ConfirmationDialog message={`Dzēst nozīmi “${senses[deletingIndex]?.gloss}” no melnraksta?`}
          busy={saving} error={error}
          onConfirm={confirmSenseDeletion}
          onCancel={cancelSenseDeletion} />
      )}
      {error && editingIndex === null && deletingIndex === null && <p className="error">{error}</p>}
    </section>
  )
  return renderEntry(content, editable ? () => addSense() : undefined)
}
