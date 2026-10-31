import { useEffect, useState, type ChangeEvent, type FocusEvent } from 'react'
import { getDuplicateReviewCandidates } from '../api'
import { suggestionStatusLabels } from '../labels'
import type { DuplicateReviewCandidate, Review } from '../types'
import { ConfirmationDialog } from './ConfirmationDialog'

type Props = {
  excludedCaseId?: string
  initialQuery: string
  onSelect: (reviewId: string) => Promise<void>
  onCreate: () => Promise<boolean>
  selectedCase?: Review | null
}

export function DuplicateReviewCombobox({ excludedCaseId, initialQuery, onSelect, onCreate, selectedCase }: Props) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [candidates, setCandidates] = useState<DuplicateReviewCandidate[]>([])
  const [searching, setSearching] = useState(false)
  const [error, setError] = useState('')
  const [confirmation, setConfirmation] = useState<DuplicateReviewCandidate | null>(null)
  const [saving, setSaving] = useState(false)
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    const term = query.trim()
    if (!open || term.length < 2) return

    let active = true
    const timeout = window.setTimeout(async () => {
      try {
        const loaded = await getDuplicateReviewCandidates(term, excludedCaseId)
        if (active) setCandidates(loaded)
      } catch (cause) {
        if (active) {
          setError(cause instanceof Error
            ? cause.message
            : 'Neizdevās meklēt dublikātus.')
        }
      } finally {
        if (active) setSearching(false)
      }
    }, 300)

    return () => {
      active = false
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

  function closeOnBlur(event: FocusEvent<HTMLDivElement>) {
    if (!event.currentTarget.contains(event.relatedTarget)) closeDropdown()
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
    if ((!confirmation && !creating) || saving) return
    setSaving(true)
    setError('')
    try {
      if (creating) {
        if (!await onCreate()) {
          setError('Neizdevās izveidot izskatīšanu. Mēģiniet vēlreiz.')
          return
        }
      } else if (confirmation) {
        await onSelect(confirmation.id)
      }
      setConfirmation(null)
      setCreating(false)
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
    (candidate) => candidate.id !== excludedCaseId && candidate.id !== selectedCase?.id,
  )
  let confirmationMessage = 'Ieteikumam tiks izveidota jauna izskatīšana ar statusu “Apstrādē”.'
  if (confirmation) {
    confirmationMessage = `Ieteikums tiks pievienots izskatīšanai “${confirmation.reviewedTerm}” kā dublikāts. Mērķa izskatīšana paliks statusā “${suggestionStatusLabels[confirmation.status]}”.`
    if (selectedCase) {
      confirmationMessage = `Visi šīs izskatīšanas ieteikumi tiks pievienoti izskatīšanai “${confirmation.reviewedTerm}”. Mērķa izskatīšanas dati un statuss “${suggestionStatusLabels[confirmation.status]}” paliks nemainīti. Pašreizējā izskatīšana un visi tās melnraksti tiks neatgriezeniski dzēsti; atšķirīgā informācija netiks apvienota.`
    }
  }

  return (
    <div
      className="tezaurs-dropdown duplicate-review-dropdown"
      onBlur={closeOnBlur}
      onKeyDown={(event) => {
        if (event.key === 'Escape') closeDropdown()
      }}
    >
      <div className="tezaurs-dropdown-control">
        <button
          aria-expanded={open}
          className="tezaurs-dropdown-toggle"
          disabled={saving}
          onClick={() => open ? closeDropdown() : openDropdown()}
          type="button"
        >
          <span className="tezaurs-option-label">
            {selectedCase ? 'Jauna izskatīšana' : 'Izvēlieties izskatīšanu...'}
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
                      {suggestionStatusLabels[candidate.status]} · {candidate.sourceCount} ieteikumi
                    </small>
                  </span>
                </button>
              ))}
            <button type="button" onClick={() => {
              closeDropdown()
              setError('')
              if (!selectedCase) setCreating(true)
            }}>Jauna izskatīšana</button>
          </div>
        </div>
      )}
      {(confirmation || creating) && (
        <ConfirmationDialog
          message={confirmationMessage}
          busy={saving}
          error={error}
          onConfirm={() => void confirmDuplicate()}
          onCancel={() => {
            setConfirmation(null)
            setCreating(false)
          }}
        />
      )}
    </div>
  )
}
