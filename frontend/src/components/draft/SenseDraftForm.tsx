import { useEffect, useState, type ReactNode } from 'react'
import {
  getSenseDrafts,
  getTezaursSenses,
  getTezaursFormOptions,
  saveSense,
  deleteSense,
  getExamplesForCase,
} from '../../api/client'
import type {
  Review,
  SaveSense,
  TezaursFormOptions,
  TezaursSense,
  Example,
} from '../../api/types'
import { EditDialog } from '../shared/EditDialog'
import { SenseActions } from '../tezaurs/TezaursActions'
import { ConfirmationDialog } from '../shared/ConfirmationDialog'
import { SenseFields } from './SenseFields'
import { SelectedExamples } from '../usage/SelectedExamples'

type Props = {
  reviewId: string
  initialGloss: string
  matchedEntryId: number | null
  editable: boolean
  onReviewUpdated: (review: Review) => void
  renderEntry: (senses: ReactNode, onAddSense?: () => void) => ReactNode
}

type SenseEditAction = 'edit' | 'move' | 'promote'

type EditableSense = SaveSense & { exampleIds?: string[] }

function newSense(gloss: string): EditableSense {
  return {
    gloss,
    parentDraftSenseId: null,
    parentTezaursSenseId: null,
    flags: {},
  }
}

export function SenseDraftForm({
  reviewId,
  initialGloss,
  matchedEntryId,
  editable,
  onReviewUpdated,
  renderEntry,
}: Props) {
  const [senses, setSenses] = useState<EditableSense[]>([])
  const [existingSenses, setExistingSenses] = useState<TezaursSense[]>([])
  const [options, setOptions] = useState<TezaursFormOptions | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [savedSenses, setSavedSenses] = useState<EditableSense[]>([])
  const [editingIndex, setEditingIndex] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [editingParent, setEditingParent] = useState(false)
  const [deletingIndex, setDeletingIndex] = useState<number | null>(null)
  const [examples, setExamples] = useState<Example[]>([])

  useEffect(() => {
    let requestIsOutdated = false
    const parentSensesRequest = matchedEntryId === null
      ? Promise.resolve([])
      : getTezaursSenses(matchedEntryId)

    async function loadSenseForm() {
      try {
        const [loadedSenses, loadedOptions, loadedExistingSenses, loadedExamples] = await Promise.all([
          getSenseDrafts(reviewId),
          getTezaursFormOptions(),
          parentSensesRequest,
          getExamplesForCase(reviewId),
        ])
        if (requestIsOutdated) {
          return
        }

        setSenses(loadedSenses)
        setSavedSenses(loadedSenses)
        setOptions(loadedOptions)
        setExistingSenses(loadedExistingSenses)
        setExamples(loadedExamples)
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
  }, [matchedEntryId, reviewId])

  function replaceSense(index: number, updatedSense: EditableSense) {
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

  async function saveEditedSense() {
    if (editingIndex === null) return false
    const editedSense = senses[editingIndex]
    setSaving(true)
    setError('')
    try {
      const response = await saveSense(reviewId, { ...editedSense, approved: true })
      const updatedSenses = senses.map((sense, index) => index === editingIndex ? response.sense : sense)
      setSenses(updatedSenses)
      setSavedSenses(updatedSenses)
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
    const senseId = senses[deletingIndex].id
    if (!senseId) return
    setSaving(true)
    setError('')
    try {
      const review = await deleteSense(reviewId, senseId)
      const remainingSenses = savedSenses.filter((sense) => sense.id !== senseId)
      setSenses(remainingSenses)
      setSavedSenses(remainingSenses)
      setDeletingIndex(null)
      onReviewUpdated(review)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās dzēst nozīmi.')
    } finally {
      setSaving(false)
    }
  }

  if (loading) {
    return renderEntry(<p className="muted">Ielādē nozīmju formu...</p>)
  }
  if (!options) {
    return renderEntry(<p className="error">{error || 'Nozīmju forma nav pieejama.'}</p>)
  }

  const savedSensesForDisplay = senses.filter((sense) => sense.id)

  function renderSenses(parentId: string | null, parentTezaursId: number | null, prefix = ''): ReactNode {
    const siblings = savedSensesForDisplay.filter((sense) =>
      sense.parentDraftSenseId === parentId && sense.parentTezaursSenseId === parentTezaursId)
    const existingSiblingCount = parentId === null
      ? existingSenses.filter((sense) => sense.parentSenseId === parentTezaursId).length : 0
    return siblings.map((sense, siblingIndex) => {
      const index = senses.indexOf(sense)
      const position = existingSiblingCount + siblingIndex + 1
      const number = prefix ? `${prefix}.${position}` : String(position)
      const hasChildren = senses.some((child) => child.parentDraftSenseId === sense.id)
      const canDelete = editable && !hasChildren
      const isSubsense = parentId !== null || sense.parentTezaursSenseId !== null
      return (
        <div className="draft-sense" key={sense.id}>
          <SenseActions subsense={parentId !== null || sense.parentTezaursSenseId !== null} readOnly={!editable}
            onEdit={() => editSense(index, 'edit')}
            onAddSubsense={editable && !isSubsense ? () => addSense(sense.id) : undefined}
            onMove={editable && !hasChildren && (existingSenses.length > 0 || savedSensesForDisplay.length > 1)
              ? () => editSense(index, 'move') : undefined}
            onPromote={editable && (parentId !== null || sense.parentTezaursSenseId !== null)
              ? () => editSense(index, 'promote') : undefined}
            onDelete={canDelete ? () => {
              setError('')
              setDeletingIndex(index)
            } : undefined} />
          <strong>{number}.</strong>{' '}{sense.gloss || 'Skaidrojums nav norādīts.'}
          <SelectedExamples examples={examples.filter((example) => sense.exampleIds?.includes(example.id))} busy={saving} />
          {!isSubsense && renderSenses(sense.id ?? null, null, number)}
        </div>
      )
    })
  }

  function existingSenseNumber(senseId: number): string {
    const sense = existingSenses.find((existingSense) => existingSense.id === senseId)
    if (!sense) return ''
    const siblings = existingSenses.filter((existingSense) => existingSense.parentSenseId === sense.parentSenseId)
    const position = siblings.indexOf(sense) + 1
    return sense.parentSenseId === null ? String(position) : `${existingSenseNumber(sense.parentSenseId)}.${position}`
  }

  const existingParentIds: number[] = []
  for (const sense of savedSensesForDisplay) {
    const parentId = sense.parentTezaursSenseId
    if (typeof parentId === 'number' && !existingParentIds.includes(parentId)) {
      existingParentIds.push(parentId)
    }
  }

  const content = (
    <section aria-label="Nozīmes">
      {renderSenses(null, null)}
      {existingParentIds.map((parentId) => (
        <div key={parentId}>{renderSenses(null, parentId, existingSenseNumber(parentId))}</div>
      ))}
      {senses.map((sense, index) => {
        let editorTitle = sense.id ? 'Labot nozīmi' : 'Pievienot nozīmi'
        if (editingParent) {
          editorTitle = 'Pārvietot nozīmi'
        }
        return (
          <div key={sense.id ?? index}>
            {editingIndex === index && (
              <EditDialog title={editable ? editorTitle : "Nozīmes dati"} busy={saving} readOnly={!editable}
                error={error} onCancel={cancelSenseEdit} onSubmit={() => saveEditedSense()}>
                <SenseFields sense={sense} options={options} existingSenses={existingSenses}
                  draftSenses={senses} onlyParent={editingParent}
                  onChange={(updatedSense) => replaceSense(index, updatedSense)}
                  onParentChange={(value) => updateParent(index, value)} />
              </EditDialog>
            )}
          </div>
        )
      })}
      {savedSensesForDisplay.length === 0 && <p className="muted">Jaunas nozīmes vēl nav pievienotas.</p>}
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
