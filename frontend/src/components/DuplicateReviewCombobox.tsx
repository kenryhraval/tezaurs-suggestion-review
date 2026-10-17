import { useEffect, useState, type ChangeEvent, type FocusEvent } from 'react'
import { getDuplicateReviewCandidates } from '../api'
import { suggestionStatusLabels } from '../labels'
import type { DuplicateReviewCandidate } from '../types'

type Props = {
  initialQuery: string
  onSelect: (reviewId: string) => Promise<void>
}

export function DuplicateReviewCombobox({ initialQuery, onSelect }: Props) {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [candidates, setCandidates] = useState<DuplicateReviewCandidate[]>([])
  const [searching, setSearching] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    const term = query.trim()
    if (!open || term.length < 2) return

    let active = true
    const timeout = window.setTimeout(async () => {
      try {
        const loaded = await getDuplicateReviewCandidates(term)
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
  }, [open, query])

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

  async function choose(candidate: DuplicateReviewCandidate) {
    setError('')
    try {
      await onSelect(candidate.id)
      closeDropdown()
    } catch (cause) {
      setError(cause instanceof Error
        ? cause.message
        : 'Neizdevās piesaistīt dublikātu.')
    }
  }

  const term = query.trim()

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
          onClick={() => open ? closeDropdown() : openDropdown()}
          type="button"
        >
          <span>Piesaistīt kā dublikātu...</span>
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
            {!searching && !error && term.length >= 2 && candidates.length === 0 && (
              <small>Atbilstoša izskatīšana netika atrasta.</small>
            )}
          </div>

          {candidates.length > 0 && (
            <div className="tezaurs-dropdown-options">
              {candidates.map((candidate) => (
                <button key={candidate.id} onClick={() => void choose(candidate)} type="button">
                  <strong>{candidate.reviewedTerm}</strong>
                  <small>
                    {suggestionStatusLabels[candidate.status]} · {candidate.sourceCount} ieteikumi
                  </small>
                </button>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
