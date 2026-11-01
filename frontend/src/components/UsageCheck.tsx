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
  const [expanded, setExpanded] = useState(defaultOpen)
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

  async function confirmDecision() {
    if (!pendingDecision) {
      return
    }
    setSaving(true)
    setError('')
    try {
      if (await onSaveDecision(pendingDecision)) {
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
    setPendingDecision('USED')
  }

  function chooseNotUsed() {
    setError('')
    const previousDecisionWasInvented = review.usageDecision === 'INVENTED'
    setPendingDecision(previousDecisionWasInvented ? 'INVENTED' : 'NOT_CONFIRMED')
  }

  async function saveCorpusEvidence(request: SaveUsageEvidence) {
    await addEvidence(request)
  }

  let decisionSummary = 'Nē, neatzīstu. Trūkst datu.'
  if (review.usageDecision === 'USED') {
    decisionSummary = 'Jā, atzīstu.'
  } else if (review.usageDecision === 'INVENTED') {
    decisionSummary = 'Nē, neatzīstu. Izdomāts vārds.'
  }

  return (
    <section className="usage-check">
      <details open={expanded} onToggle={toggleSection}>
        <summary className={!expanded && decided ? 'confirmed' : undefined}>
          <strong>3. Lietojuma pārbaude</strong>
        </summary>
        {editable && <>
          {searchStarted && <CorpusSearch reviewId={review.id} term={review.reviewedTerm}
            evidence={evidence} saving={saving || loading} onSave={saveCorpusEvidence}
            onRemove={requestEvidenceRemoval}
            onAddSource={openSourceForm} />}
        </>}
        {error && <p className="error" role="alert">{error}</p>}
        {loading && <p className="muted">Ielādē saglabātos atradumus...</p>}
        <SelectedUsageEvidence evidence={evidence} busy={saving}
          onRemove={editable ? requestEvidenceRemoval : undefined} />
        {editable && <>
          <p><strong>Vai atzīstat, ka vārdu lieto?</strong></p>
          <div>
            <button type="button" aria-pressed={review.usageDecision === 'USED'} disabled={saving || loading}
              onClick={chooseUsed}>Jā, atzīstu</button>{' '}
            <button type="button" aria-pressed={review.usageDecision === 'NOT_CONFIRMED' || review.usageDecision === 'INVENTED'} disabled={saving || loading}
              onClick={chooseNotUsed}>Nē, neatzīstu</button>
          </div>
        </>}
      </details>
      {!expanded && decided && <p className="muted">{decisionSummary}</p>}
      {addingSource && <EditDialog title="Pievienot citu avotu" busy={saving} error={error} wide={false}
        onSubmit={(event) => addExternalEvidence(event)}
        onCancel={closeSourceForm}>
        <label>Avota saite<input autoFocus name="url" type="url" required maxLength={4000} disabled={saving} /></label>
        <label>Citāts<textarea name="citation" required maxLength={10000} rows={3} disabled={saving} /></label>
      </EditDialog>}
      {pendingDecision && <ConfirmationDialog busy={saving} error={error}
        message={pendingDecision === 'USED'
          ? 'Apstiprinot varēsiet sagatavot jaunā šķirkļa melnrakstu.'
          : 'Kā rīkoties ar ieteikumu?'}
        onConfirm={() => confirmDecision()} onCancel={() => setPendingDecision(null)}>
        {pendingDecision !== 'USED' && <>
          <label className="checkbox-label"><input type="radio" name="usage-outcome" disabled={saving}
            checked={pendingDecision === 'NOT_CONFIRMED'} onChange={() => setPendingDecision('NOT_CONFIRMED')} />
            Atlikt: trūkst datu, izpēti var turpināt vēlāk.
          </label>
          <label className="checkbox-label"><input type="radio" name="usage-outcome" disabled={saving}
            checked={pendingDecision === 'INVENTED'} onChange={() => setPendingDecision('INVENTED')} />
            Izdomāts vārds.
          </label>
        </>}
      </ConfirmationDialog>}
      {deletingId && <ConfirmationDialog busy={saving} error={error}
        message="Noņemt šo atradumu no izskatīšanas?" onConfirm={() => removeEvidence()}
        onCancel={() => setDeletingId(null)} />}
    </section>
  )
}
