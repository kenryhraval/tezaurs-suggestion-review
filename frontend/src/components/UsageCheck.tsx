import { useEffect, useState, type FormEvent, type SyntheticEvent } from 'react'
import { deleteUsageEvidence, getUsageEvidence, saveUsageEvidence } from '../api/client'
import type { Review, SaveUsageEvidence, UsageDecision, UsageEvidence } from '../api/types'
import { ConfirmationDialog } from './ConfirmationDialog'
import { CorpusSearch } from './CorpusSearch'
import { EditDialog } from './EditDialog'
import { SelectedUsageEvidence } from './SelectedUsageEvidence'

type Props = {
  review: Review
  defaultOpen: boolean
  readOnly: boolean
  onSaveDecision: (decision: UsageDecision) => Promise<boolean>
}

export function UsageCheck({ review, onSaveDecision, defaultOpen, readOnly }: Props) {
  const [expandedByUser, setExpanded] = useState(defaultOpen)
  const [evidence, setEvidence] = useState<UsageEvidence[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [addingSource, setAddingSource] = useState(false)
  const [searchStarted, setSearchStarted] = useState(defaultOpen)
  const [pendingDecision, setPendingDecision] = useState<UsageDecision | null>(null)
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const editable = !readOnly
  const decided = review.usageDecision !== 'NOT_CHECKED'
  const canCollapse = readOnly || decided
  const expanded = expandedByUser || !canCollapse

  useEffect(() => {
    let requestIsOutdated = false
    async function loadSavedEvidence() {
      try {
        const loaded = await getUsageEvidence(review.id)
        if (!requestIsOutdated) {
          setEvidence(loaded)
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

    loadSavedEvidence()
    return () => {
      requestIsOutdated = true
    }
  }, [review.id])

  async function addEvidence(request: SaveUsageEvidence) {
    setSaving(true)
    setError('')
    try {
      const saved = await saveUsageEvidence(review.id, request)
      setEvidence((current) => {
        if (current.some((item) => item.id === saved.id)) {
          return current
        }
        return [...current, saved]
      })
      return true
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt atradumu.')
      return false
    } finally {
      setSaving(false)
    }
  }

  async function addExternalEvidence(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    const saved = await addEvidence({
      source: 'EXTERNAL',
      corpus: null,
      tokenNumber: null,
      query: review.reviewedTerm,
      citation: String(data.get('citation')),
      url: String(data.get('url')),
      reference: null,
      note: null,
    })
    if (saved) {
      setAddingSource(false)
    }
  }

  async function saveDecision(decision: UsageDecision) {
    if (saving) {
      return
    }
    setSaving(true)
    setError('')
    try {
      if (await onSaveDecision(decision)) {
        setPendingDecision(null)
        setExpanded(false)
      }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt izvēli.')
    } finally {
      setSaving(false)
    }
  }

  async function removeEvidence() {
    if (!deletingId) {
      return
    }
    setSaving(true)
    setError('')
    try {
      await deleteUsageEvidence(review.id, deletingId)
      setEvidence((current) => current.filter((item) => item.id !== deletingId))
      setDeletingId(null)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās noņemt atradumu.')
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

  function requestEvidenceRemoval(id: string) {
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

  function chooseUsed() {
    setError('')
    saveDecision('USED')
  }

  function chooseNotUsed() {
    setError('')
    const previousDecisionWasInvented = review.usageDecision === 'INVENTED'
    setPendingDecision(previousDecisionWasInvented ? 'INVENTED' : 'NOT_CONFIRMED')
  }

  async function saveCorpusEvidence(request: SaveUsageEvidence) {
    await addEvidence(request)
  }

  return (
    <section className={`usage-check review-section${expanded ? ' is-open' : ''}`}>
      <details open={expanded} onToggle={toggleSection}>
        <summary className={!expanded && decided ? 'section-title confirmed' : 'section-title'}
          aria-disabled={!canCollapse} onClick={(event) => {
            if (!canCollapse) event.preventDefault()
          }}>
          3. Lietojuma pārbaude
        </summary>
        {editable && <>
          {searchStarted && <CorpusSearch reviewId={review.id} term={review.reviewedTerm}
            evidence={evidence} saving={saving || loading} onSave={saveCorpusEvidence}
            onRemove={requestEvidenceRemoval}
            onAddSource={openSourceForm} />}
        </>}
        {error && <p className="error" role="alert">{error}</p>}
        {loading && <p className="muted">Ielādē saglabātos atradumus...</p>}
        {readOnly && !loading && !error && evidence.length === 0 && (
          <p className="muted">Nav atzīmētu piemēru.</p>
        )}
        <SelectedUsageEvidence evidence={evidence} busy={saving}
          onRemove={editable ? requestEvidenceRemoval : undefined} />
        {readOnly && review.usageNote && <p>{review.usageNote}</p>}
        <p><strong>Vai atzīstat, ka vārdu lieto?</strong></p>
        <div>
          <button className="btn btn-sm btn-outline-secondary" type="button" aria-pressed={review.usageDecision === 'USED'} disabled={readOnly || saving || loading}
            onClick={chooseUsed}>Jā, atzīstu</button>{' '}
          <button className="btn btn-sm btn-outline-secondary" type="button" aria-pressed={review.usageDecision === 'NOT_CONFIRMED' || review.usageDecision === 'INVENTED'} disabled={readOnly || saving || loading}
            onClick={chooseNotUsed}>Nē, neatzīstu</button>
        </div>
      </details>
      {addingSource && <EditDialog title="Pievienot citu avotu" busy={saving} error={error} wide={false}
        onSubmit={(event) => addExternalEvidence(event)}
        onCancel={closeSourceForm}>
        <label>Avota saite<input className="form-control form-control-sm" autoFocus name="url" type="url" required maxLength={4000} disabled={saving} /></label>
        <label>Citāts<textarea className="form-control form-control-sm" name="citation" required maxLength={10000} rows={3} disabled={saving} /></label>
      </EditDialog>}
      {pendingDecision && <ConfirmationDialog busy={saving} error={error}
        message="Kā rīkoties ar ieteikumu?"
        onConfirm={() => saveDecision(pendingDecision)} onCancel={() => setPendingDecision(null)}>
        <>
          <label className="checkbox-label"><input type="radio" name="usage-outcome" disabled={saving}
            checked={pendingDecision === 'NOT_CONFIRMED'} onChange={() => setPendingDecision('NOT_CONFIRMED')} />
            Atlikt: trūkst datu, izpēti var turpināt vēlāk.
          </label>
          <label className="checkbox-label"><input type="radio" name="usage-outcome" disabled={saving}
            checked={pendingDecision === 'INVENTED'} onChange={() => setPendingDecision('INVENTED')} />
            Izdomāts vārds.
          </label>
        </>
      </ConfirmationDialog>}
      {deletingId && <ConfirmationDialog busy={saving} error={error}
        message="Noņemt šo atradumu no izskatīšanas?" onConfirm={() => removeEvidence()}
        onCancel={() => setDeletingId(null)} />}
    </section>
  )
}
