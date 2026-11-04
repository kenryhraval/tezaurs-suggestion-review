import { useEffect, useState, type ChangeEvent, type FocusEvent, type KeyboardEvent } from 'react'
import { getDuplicateReviewCandidates } from '../api/client'
import { reviewStatusLabels } from '../review/statuses'
import type { DuplicateReviewCandidate, Review } from '../api/types'
import { ConfirmationDialog } from './ConfirmationDialog'

type Props = {
  excludedCaseId?: string
  initialQuery: string
  onSelect: (reviewId: string) => Promise<void>
  currentCase?: Review | null
}

export function DuplicateReviewCombobox({ excludedCaseId, initialQuery, onSelect, currentCase }: Props) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [candidates, setCandidates] = useState<DuplicateReviewCandidate[]>([])
  const [searching, setSearching] = useState(false)
  const [error, setError] = useState('')
  const [confirmation, setConfirmation] = useState<DuplicateReviewCandidate | null>(null)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    const term = query.trim()
    if (!open || term.length < 2) {
      return
    }

    let requestIsOutdated = false
    async function searchDuplicateCases() {
      try {
        const loaded = await getDuplicateReviewCandidates(term, excludedCaseId)
        if (!requestIsOutdated) {
          setCandidates(loaded)
        }
      } catch (cause) {
        if (!requestIsOutdated) {
          setError(cause instanceof Error
            ? cause.message
            : 'Neizdevās meklēt dublikātus.')
        }
      } finally {
        if (!requestIsOutdated) {
          setSearching(false)
        }
      }
    }

    const timeout = window.setTimeout(searchDuplicateCases, 300)

    return () => {
      requestIsOutdated = true
      window.clearTimeout(timeout)
    }
  }, [open, query, excludedCaseId])

  function openDropdown() {
    setQuery(initialQuery)
    setCandidates([])
    setError('')
    setSearching(initialQuery.trim().length >= 2)
    setOpen(true)
  }

  function closeDropdown() {
    setOpen(false)
    setSearching(false)
  }

  function toggleDropdown() {
    if (open) {
      closeDropdown()
    } else {
      openDropdown()
    }
  }

  function closeOnEscape(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      closeDropdown()
    }
  }

  function chooseNewCase() {
    closeDropdown()
    setError('')
  }

  function cancelConfirmation() {
    setConfirmation(null)
  }

  function closeOnBlur(event: FocusEvent<HTMLDivElement>) {
    if (!event.currentTarget.contains(event.relatedTarget)) {
      closeDropdown()
    }
  }

  function changeQuery(event: ChangeEvent<HTMLInputElement>) {
    const value = event.target.value
    setQuery(value)
    setCandidates([])
    setError('')
    setSearching(value.trim().length >= 2)
  }

  function choose(candidate: DuplicateReviewCandidate) {
    setError('')
    closeDropdown()
    setConfirmation(candidate)
  }

  async function confirmDuplicate() {
    if (!confirmation || saving) {
      return
    }
    setSaving(true)
    setError('')
    try {
      await onSelect(confirmation.id)
      setConfirmation(null)
    } catch (cause) {
      setError(cause instanceof Error
        ? cause.message
        : 'Neizdevās piesaistīt dublikātu.')
    } finally {
      setSaving(false)
    }
  }

  const term = query.trim()
  const visibleCandidates = candidates.filter(
    (candidate) => candidate.id !== excludedCaseId && candidate.id !== currentCase?.id,
  )
  let confirmationMessage = ''
  if (confirmation) {
    confirmationMessage = `Ieteikums tiks pievienots izskatīšanai “${confirmation.reviewedTerm}” kā dublikāts. Mērķa izskatīšana paliks statusā “${reviewStatusLabels[confirmation.status]}”.`
    if (currentCase) {
      confirmationMessage = `Visi šīs izskatīšanas ieteikumi tiks pievienoti izskatīšanai “${confirmation.reviewedTerm}”. Mērķa izskatīšanas dati un statuss “${reviewStatusLabels[confirmation.status]}” paliks nemainīti. Pašreizējā izskatīšana, tās atradumi un melnraksti tiks neatgriezeniski dzēsti; atšķirīgā informācija netiks apvienota.`
    }
  }

  return (
    <div
      className="tezaurs-dropdown duplicate-review-dropdown"
      onBlur={closeOnBlur}
      onKeyDown={closeOnEscape}
    >
      <div className="tezaurs-dropdown-control">
        <button
          aria-expanded={open}
          className="tezaurs-dropdown-toggle"
          disabled={saving}
          onClick={toggleDropdown}
          type="button"
        >
          <span className="tezaurs-option-label">
            Jauna izskatīšana
          </span>
          <span aria-hidden="true">▾</span>
        </button>
      </div>

      {open && (
        <div className="tezaurs-dropdown-menu">
          <div className="tezaurs-combobox-search">
            <label>
              Meklēt esošu izskatīšanu
              <input autoFocus onChange={changeQuery} type="search" value={query} />
            </label>
            {term.length === 1 && <small>Ievadiet vēl vienu burtu.</small>}
            {searching && <small>Meklē...</small>}
            {error && <small className="error">{error}</small>}
            {!searching && !error && term.length >= 2 && visibleCandidates.length === 0 && (
              <small>Atbilstoša izskatīšana netika atrasta.</small>
            )}
          </div>

          <div className="tezaurs-dropdown-options">
              {visibleCandidates.map((candidate) => (
                <button key={candidate.id} onClick={() => choose(candidate)} type="button">
                  <span className="tezaurs-option-label">
                    <strong>{candidate.reviewedTerm}</strong>
                    <small>
                      {reviewStatusLabels[candidate.status]} · {candidate.sourceCount} ieteikumi
                    </small>
                  </span>
                </button>
              ))}
            <button type="button" onClick={chooseNewCase}>Jauna izskatīšana</button>
          </div>
        </div>
      )}
      {confirmation && (
        <ConfirmationDialog
          message={confirmationMessage}
          busy={saving}
          error={error}
          onConfirm={() => confirmDuplicate()}
          onCancel={cancelConfirmation}
        />
      )}
    </div>
  )
}
