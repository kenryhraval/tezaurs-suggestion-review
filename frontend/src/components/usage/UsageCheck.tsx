import { useEffect, useState, type FormEvent, type SyntheticEvent } from 'react'
import { deleteCorpusExamples, deleteExample, getExamplesForCase, saveExamples, selectCorpusExamples } from '../../api/client'
import type { Review, SaveExample, SelectCorpusExamples, UsageDecision, Example } from '../../api/types'
import { ConfirmationDialog } from '../shared/ConfirmationDialog'
import { CorpusSearch } from './CorpusSearch'
import { EditDialog } from '../shared/EditDialog'
import { SelectedExamples } from './SelectedExamples'

type Props = {
  review: Review
  defaultOpen: boolean
  readOnly: boolean
  onSaveDecision: (decision: UsageDecision) => Promise<void>
  onExamplesChange: () => void
}

const decisionLabels: Record<UsageDecision, string> = {
  USED: 'Lietojums atzīts',
  NOT_CONFIRMED: 'Trūkst datu',
  INVENTED: 'Izdomāts vārds',
}

export function UsageCheck({ review, onSaveDecision, onExamplesChange, defaultOpen, readOnly }: Props) {
  const [expandedByUser, setExpanded] = useState(defaultOpen)
  const [examples, setExamples] = useState<Example[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [addingSource, setAddingSource] = useState(false)
  const [searchStarted, setSearchStarted] = useState(defaultOpen)
  const [decisionDropdownOpen, setDecisionDropdownOpen] = useState(false)
  const [pendingDecision, setPendingDecision] = useState<UsageDecision | null>(null)
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const editable = !readOnly
  const needsUsageDecision = review.type === 'ENTRY'
  const usageDecision = review.usageDecision ?? null
  const decided = needsUsageDecision ? usageDecision !== null : examples.length > 0
  const canCollapse = readOnly || !needsUsageDecision || decided
  const expanded = expandedByUser || !canCollapse

  useEffect(() => {
    let requestIsOutdated = false
    async function loadSavedExamples() {
      try {
        const loaded = await getExamplesForCase(review.id)
        if (!requestIsOutdated) {
          setExamples(loaded)
        }
      } catch (cause) {
        if (!requestIsOutdated) {
          setError(cause instanceof Error ? cause.message : 'Neizdevās ielādēt atradumus.')
        }
      } finally {
        if (!requestIsOutdated) {
          setLoading(false)
        }
      }
    }

    loadSavedExamples()
    return () => {
      requestIsOutdated = true
    }
  }, [review.id])

  async function addExamples(requests: SaveExample[]) {
    setSaving(true)
    setError('')
    try {
      const saved = await saveExamples(review.id, requests)
      setExamples((current) => {
        const byId = new Map(current.map((item) => [item.id, item]))
        saved.forEach((item) => byId.set(item.id, item))
        return [...byId.values()]
      })
      onExamplesChange()
      return true
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt atradumu.')
      return false
    } finally {
      setSaving(false)
    }
  }

  async function addExternalExample(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    const saved = await addExamples([{
      source: 'EXTERNAL',
      corpus: null,
      tokenNumber: null,
      query: review.reviewedTerm,
      citation: String(data.get('citation')),
      url: String(data.get('url')),
      reference: null,
      note: null,
    }])
    if (saved) {
      setAddingSource(false)
    }
  }

  async function handleCorpusSelection(selection: SelectCorpusExamples) {
    setSaving(true)
    setError('')
    try {
      const saved = await selectCorpusExamples(review.id, selection)
      setExamples(saved)
      onExamplesChange()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās atlasīt korpusa piemērus.')
    } finally {
      setSaving(false)
    }
  }

  async function saveDecision(decision: UsageDecision) {
    if (readOnly || saving || loading) {
      return
    }
    setSaving(true)
    setError('')
    try {
      await onSaveDecision(decision)
      setPendingDecision(null)
      setExpanded(false)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt izvēli.')
    } finally {
      setSaving(false)
    }
  }

  function chooseDecision(decision: UsageDecision) {
    setDecisionDropdownOpen(false)
    setError('')
    if (decision === 'USED') {
      saveDecision(decision)
    } else {
      setPendingDecision(decision)
    }
  }

  async function removeExample() {
    if (!deletingId) {
      return
    }
    setSaving(true)
    setError('')
    try {
      await deleteExample(review.id, deletingId)
      setExamples((current) => current.filter((item) => item.id !== deletingId))
      onExamplesChange()
      setDeletingId(null)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās noņemt atradumu.')
    } finally {
      setSaving(false)
    }
  }

  async function removeCorpusExamples(corpus: string) {
    setSaving(true)
    setError('')
    try {
      const remaining = await deleteCorpusExamples(review.id, corpus)
      setExamples(remaining)
      onExamplesChange()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās noņemt korpusa piemērus.')
    } finally {
      setSaving(false)
    }
  }

  function toggleSection(event: SyntheticEvent<HTMLDetailsElement>) {
    const isOpen = event.currentTarget.open
    setExpanded(isOpen)
    if (isOpen && editable) {
      setSearchStarted(true)
    }
  }

  function requestExampleRemoval(id: string) {
    setError('')
    setDeletingId(id)
  }

  function openSourceForm() {
    setError('')
    setAddingSource(true)
  }

  function closeSourceForm() {
    setAddingSource(false)
    setError('')
  }

  return (
    <section className={`usage-check review-section${expanded ? ' is-open' : ''}`}>
      <details open={expanded} onToggle={toggleSection}>
        <summary className={!expanded && decided ? 'section-title confirmed' : 'section-title'}
          aria-disabled={!canCollapse} onClick={(event) => {
            if (!canCollapse) event.preventDefault()
          }}>
          3. {needsUsageDecision ? 'Lietojuma pārbaude' : 'Piemēru atlase'}
        </summary>
        {editable && searchStarted && <CorpusSearch term={review.reviewedTerm}
            examples={examples} saving={saving || loading} onSave={addExamples} onSelectCorpus={handleCorpusSelection}
            onRemove={requestExampleRemoval} onRemoveCorpus={removeCorpusExamples}
            onAddSource={openSourceForm} />}
        {error && !addingSource && !deletingId && !pendingDecision && (
          <p className="error" role="alert">{error}</p>
        )}
        {loading && <p className="muted">Ielādē saglabātos atradumus...</p>}
        {readOnly && !loading && !error && examples.length === 0 && (
          <p className="muted">Nav atzīmētu piemēru.</p>
        )}
        <SelectedExamples examples={examples} busy={saving}
          onRemove={editable ? requestExampleRemoval : undefined} />
        {readOnly && review.usageNote && <p>{review.usageNote}</p>}
        {needsUsageDecision && <>
        <p><strong>Vai atzīstat, ka vārdu lieto?</strong></p>
        <div className="tezaurs-dropdown" onBlur={(event) => {
          if (!event.currentTarget.contains(event.relatedTarget)) {
            setDecisionDropdownOpen(false)
          }
        }} onKeyDown={(event) => {
          if (event.key === 'Escape') setDecisionDropdownOpen(false)
        }}>
          <div className="tezaurs-dropdown-control">
            <button className="tezaurs-dropdown-toggle" type="button" aria-expanded={decisionDropdownOpen}
              aria-label="Lietojuma izvērtējums" disabled={readOnly || saving || loading}
              onClick={() => setDecisionDropdownOpen(!decisionDropdownOpen)}>
              <span className="tezaurs-option-label">{usageDecision === null ? 'Nav izvēlēts' : decisionLabels[usageDecision]}</span>
              <span aria-hidden="true">▾</span>
            </button>
          </div>
          {decisionDropdownOpen && <div className="tezaurs-dropdown-menu">
            <div className="tezaurs-dropdown-options">
              <button type="button" disabled>Nav izvēlēts</button>
              <button type="button" onClick={() => chooseDecision('USED')}>{decisionLabels.USED}</button>
              <button type="button" onClick={() => chooseDecision('NOT_CONFIRMED')}>{decisionLabels.NOT_CONFIRMED}</button>
              <button type="button" onClick={() => chooseDecision('INVENTED')}>{decisionLabels.INVENTED}</button>
            </div>
          </div>}
        </div>
        </>}
      </details>
      {pendingDecision && <ConfirmationDialog busy={saving} error={error}
        message={pendingDecision === 'NOT_CONFIRMED'
          ? 'Perēklis tiks atlikts stāvoklī “Trūkst datu”. Lai turpinātu darbu, tas būs jāpaņem apstrādē.'
          : 'Vārds tiks atzīmēts kā izdomāts, un perēklis pāries stāvoklī “Izdoma”. Lai turpinātu darbu, tas būs jāpaņem apstrādē.'}
        onConfirm={() => saveDecision(pendingDecision)}
        onCancel={() => {
          setPendingDecision(null)
          setError('')
        }} />}
      {addingSource && <EditDialog title="Pievienot citu avotu" busy={saving} error={error} wide={false}
        onSubmit={(event) => addExternalExample(event)}
        onCancel={closeSourceForm}>
        <label>Avota saite<input className="form-control form-control-sm" autoFocus name="url" type="url" required maxLength={4000} disabled={saving} /></label>
        <label>Citāts<textarea className="form-control form-control-sm" name="citation" required maxLength={10000} rows={3} disabled={saving} /></label>
      </EditDialog>}
      {deletingId && <ConfirmationDialog busy={saving} error={error}
        message="Noņemt šo atradumu no izskatīšanas?" onConfirm={() => removeExample()}
        onCancel={() => {
          setDeletingId(null)
          setError('')
        }} />}
    </section>
  )
}
