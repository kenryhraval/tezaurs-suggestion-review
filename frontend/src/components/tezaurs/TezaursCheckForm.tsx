import { useEffect, useRef, useState, type ChangeEvent, type FocusEvent, type KeyboardEvent, type SyntheticEvent } from 'react'
import { getTezaursCandidates, getTezaursEntry } from '../../api/client'
import type { TezaursEntryCandidate, ReviewType, ReviewStatus, SaveReviewType } from '../../api/types'
import { ConfirmationDialog } from '../shared/ConfirmationDialog'
import { TezaursEntryLink } from './TezaursEntryLink'
import { TezaursSensePreview } from './TezaursSensePreview'

type Props = {
  reviewedTerm: string
  reviewType: ReviewType
  reviewStatus: ReviewStatus
  matchedEntryId: number | null
  readOnly: boolean
  defaultOpen: boolean
  onSave: (selection: SaveReviewType) => Promise<void>
}

const NOT_FOUND = 'not-found'

function initialSelection(reviewType: ReviewType, matchedEntryId: number | null) {
  if (reviewType === 'ENTRY') {
    return NOT_FOUND
  }
  return matchedEntryId === null ? '' : String(matchedEntryId)
}

export function TezaursCheckForm({
  reviewedTerm,
  reviewType,
  reviewStatus,
  matchedEntryId,
  readOnly,
  defaultOpen,
  onSave,
}: Props) {
  const [candidates, setCandidates] = useState<TezaursEntryCandidate[]>([])
  const [loading, setLoading] = useState(matchedEntryId !== null)
  const [serverError, setServerError] = useState('')
  const [selection, setSelection] = useState(() => initialSelection(reviewType, matchedEntryId))
  const [customSelection, setCustomSelection] = useState<TezaursEntryCandidate | null>(null)
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [query, setQuery] = useState(reviewedTerm)
  const [searching, setSearching] = useState(false)
  const [searchError, setSearchError] = useState('')
  const savedMeaningExists = reviewType === 'SENSE' ? reviewStatus === 'ALREADY_EXISTS' : null
  const [selectedMeaningExists, setSelectedMeaningExists] = useState<boolean | null>(savedMeaningExists)
  const [saving, setSaving] = useState(false)
  const [confirmation, setConfirmation] = useState(false)
  const [expandedByUser, setExpanded] = useState(defaultOpen)
  const dropdownRef = useRef<HTMLDivElement>(null)
  let selectedEntryId: number | null = null
  if (selection !== '' && Number.isInteger(Number(selection))) {
    selectedEntryId = Number(selection)
  }

  let selectedCandidate = candidates.find((candidate) => candidate.id === selectedEntryId)
  if (!selectedCandidate && customSelection?.id === selectedEntryId) {
    selectedCandidate = customSelection
  }

  const searchTerm = query.trim()
  let displayedCandidates = candidates
  const selectionIsInResults = candidates.some((candidate) => candidate.id === selectedCandidate?.id)
  if (selectedCandidate && !selectionIsInResults && searchTerm === reviewedTerm.trim()) {
    displayedCandidates = [selectedCandidate, ...candidates]
  }
  const confirmed = reviewType !== 'UNKNOWN'
  const selectionMatchesSaved = (reviewType === 'ENTRY' && selection === NOT_FOUND)
    || (reviewType === 'SENSE' && selectedEntryId === matchedEntryId && selectedMeaningExists === savedMeaningExists)
  const hasChanges = !selectionMatchesSaved
  const canCollapse = readOnly || (confirmed && selectionMatchesSaved)
  const expanded = expandedByUser || !canCollapse

  useEffect(() => {
    let requestIsOutdated = false
    const selectedId = matchedEntryId

    if (selectedId === null) {
      return
    }

    async function loadSelectedEntry(entryId: number) {
      try {
        const loaded = await getTezaursEntry(entryId)
        if (!requestIsOutdated) {
          setCustomSelection(loaded)
        }
      } catch (cause) {
        if (!requestIsOutdated) {
          setServerError(cause instanceof Error
            ? cause.message
            : 'Neizdevās pārbaudīt Tēzauru.')
        }
      } finally {
        if (!requestIsOutdated) {
          setLoading(false)
        }
      }
    }

    loadSelectedEntry(selectedId)
    return () => {
      requestIsOutdated = true
    }
  }, [matchedEntryId])

  useEffect(() => {
    const term = query.trim()
    if (!dropdownOpen || term === '') {
      return
    }

    let requestIsOutdated = false
    async function searchEntries() {
      try {
        const loaded = await getTezaursCandidates(term)
        if (!requestIsOutdated) {
          setCandidates(loaded)
        }
      } catch (cause) {
        if (!requestIsOutdated) {
          setSearchError(cause instanceof Error
            ? cause.message
            : 'Neizdevās pārbaudīt Tēzauru.')
        }
      } finally {
        if (!requestIsOutdated) {
          setSearching(false)
        }
      }
    }

    const searchDelay = term === reviewedTerm.trim() ? 0 : 300
    const timeout = window.setTimeout(searchEntries, searchDelay)

    return () => {
      requestIsOutdated = true
      window.clearTimeout(timeout)
    }
  }, [dropdownOpen, query, reviewedTerm])

  useEffect(() => {
    if (!dropdownOpen) {
      return
    }
    function closeDropdownOutside(event: PointerEvent) {
      if (!dropdownRef.current?.contains(event.target as Node)) {
        setDropdownOpen(false)
        setSearching(false)
      }
    }
    document.addEventListener('pointerdown', closeDropdownOutside)
    return () => document.removeEventListener('pointerdown', closeDropdownOutside)
  }, [dropdownOpen])

  function openDropdown() {
    setQuery(reviewedTerm)
    setCandidates([])
    setSearchError('')
    setSearching(reviewedTerm.trim() !== '')
    setDropdownOpen(true)
  }

  function closeDropdown() {
    setDropdownOpen(false)
    setSearching(false)
  }

  function toggleDropdown() {
    if (dropdownOpen) {
      closeDropdown()
    } else {
      openDropdown()
    }
  }

  function closeDropdownOnEscape(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      closeDropdown()
    }
  }

  function toggleSection(event: SyntheticEvent<HTMLDetailsElement>) {
    const isOpen = event.currentTarget.open
    setExpanded(isOpen)
    if (!isOpen) {
      closeDropdown()
    }
  }

  function cancelConfirmation() {
    setConfirmation(false)
    setServerError('')
  }

  function closeDropdownOnBlur(event: FocusEvent<HTMLDivElement>) {
    if (event.relatedTarget && !event.currentTarget.contains(event.relatedTarget)) {
      closeDropdown()
    }
  }

  function changeQuery(event: ChangeEvent<HTMLInputElement>) {
    const value = event.target.value
    setQuery(value)
    setCandidates([])
    setSearchError('')
    setSearching(value.trim() !== '')
  }

  function chooseCandidate(candidate: TezaursEntryCandidate) {
    if (candidate.id !== selectedEntryId) {
      setSelectedMeaningExists(null)
    }
    setCustomSelection(candidate)
    setSelection(String(candidate.id))
    closeDropdown()
  }

  function chooseNotFound() {
    closeDropdown()
    if (reviewType === 'ENTRY' && selection === NOT_FOUND) {
      return
    }
    saveSelection({ type: 'ENTRY', matchedEntryId: null })
  }

  function chooseMeaning(meaningExists: boolean) {
    if (!meaningExists) {
      if (reviewType === 'SENSE' && selectedEntryId === matchedEntryId && savedMeaningExists === false) {
        return
      }
      saveSelection({ type: 'SENSE', matchedEntryId: selectedEntryId })
      return
    }
    setServerError('')
    setConfirmation(true)
  }

  async function saveSelection(request: SaveReviewType) {
    if (saving) {
      return
    }
    setSaving(true)
    setServerError('')
    try {
      await onSave(request)
      setSelectedMeaningExists(request.type === 'SENSE' ? request.meaningAlreadyExists === true : null)
      if (request.type === 'ENTRY') {
        setSelection(NOT_FOUND)
      }
      setConfirmation(false)
      closeDropdown()
      setExpanded(false)
    } catch (cause) {
      setServerError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt izvēli.')
    } finally {
      setSaving(false)
    }
  }

  function confirmCheck() {
    saveSelection({ type: 'SENSE', matchedEntryId: selectedEntryId, meaningAlreadyExists: true })
  }

  let confirmationMessage = 'Perēklis pāries stāvoklī “Jau ir”. Esošos labojumus veiciet Tēzaurā.'
  if (confirmation && selectedEntryId !== matchedEntryId) {
    confirmationMessage += ' Iepriekšējie melnraksti paliks saglabāti pie iepriekšējās šķirkļa izvēles.'
  }

  return (
    <section className={`tezaurs-check review-section${expanded ? ' is-open' : ''}`}>
      <details open={expanded} onToggle={toggleSection}>
        <summary className={!expanded && confirmed && (readOnly || !hasChanges) ? 'section-title confirmed' : 'section-title'}
          aria-disabled={!canCollapse} onClick={(event) => {
            if (!canCollapse) event.preventDefault()
          }}>
          2. Pārbaude Tēzaurā
        </summary>

        {serverError && !confirmation && <p className="error" role="alert">{serverError}</p>}
        {loading && <p className="muted">Ielādē izvēlēto šķirkli...</p>}

        <fieldset className="review-controls" disabled={readOnly}>
          <p className="muted">Izvēlieties atbilstošo šķirkli vai norādiet, ka tāda nav.</p>
          <div
            className="tezaurs-dropdown"
            ref={dropdownRef}
            onBlur={closeDropdownOnBlur}
            onKeyDown={closeDropdownOnEscape}
          >
            <div className="tezaurs-dropdown-control">
              <button
                aria-expanded={dropdownOpen}
                aria-label="Tēzaura šķirklis"
                className="tezaurs-dropdown-toggle"
                disabled={loading || saving}
                onClick={toggleDropdown}
                type="button"
              >
                <span className="tezaurs-option-label">
                  {selectedCandidate
                    ? <><strong>{selectedCandidate.heading}</strong>
                      <small>{selectedCandidate.humanKey}</small></>
                    : selection === NOT_FOUND ? 'Šķirkļa nav Tēzaurā' : 'Izvēlieties...'}
                </span>
                <span aria-hidden="true">▾</span>
              </button>
              {selectedCandidate && <TezaursEntryLink humanKey={selectedCandidate.humanKey} />}
            </div>

            {dropdownOpen && (
              <div className="tezaurs-dropdown-menu">
                <div className="tezaurs-combobox-search">
                  <label>
                    Meklēt šķirkli
                    <input className="form-control form-control-sm" autoFocus onChange={changeQuery} type="search" value={query} />
                  </label>

                  {searching && <small>Meklē...</small>}
                  {searchError && <small className="error">{searchError}</small>}
                  {!searching && !searchError && searchTerm !== ''
                    && displayedCandidates.length === 0 && <small>Nekas netika atrasts.</small>}
                </div>

                <div className="tezaurs-dropdown-options">
                  {displayedCandidates.map((candidate) => (
                    <div key={candidate.id}>
                      <div className="tezaurs-option">
                        <button aria-pressed={candidate.id === selectedEntryId} disabled={saving}
                          onClick={() => chooseCandidate(candidate)} type="button">
                          <span className="tezaurs-option-label">
                            <strong>{candidate.heading}</strong>
                            <small>{candidate.humanKey}</small>
                          </span>
                        </button>
                        <TezaursEntryLink humanKey={candidate.humanKey} />
                      </div>
                      <TezaursSensePreview senses={candidate.senses} />
                    </div>
                  ))}
                </div>
                <button
                  aria-pressed={selection === NOT_FOUND}
                  className="tezaurs-dropdown-special"
                  disabled={saving}
                  onClick={chooseNotFound}
                  type="button"
                >
                  Šķirkļa nav Tēzaurā
                </button>
              </div>
            )}
          </div>

          {selectedEntryId !== null && selectedCandidate && (
            <div className="meaning-existence-check">
              <p><strong>
                Vai iesniegtā nozīme jau ir šķirklī “{selectedCandidate.heading}”?
              </strong></p>
              <div className="tezaurs-answer-actions">
                <button className="btn btn-sm btn-outline-secondary" aria-pressed={selectedMeaningExists === true} disabled={saving}
                  onClick={() => chooseMeaning(true)} type="button">
                  Jā, nozīme jau ir
                </button>
                <button className="btn btn-sm btn-outline-secondary" aria-pressed={selectedMeaningExists === false} disabled={saving}
                  onClick={() => chooseMeaning(false)} type="button">
                  Nē, tā ir jauna nozīme
                </button>
              </div>
            </div>
          )}
        </fieldset>

      </details>
      {confirmation && (
        <ConfirmationDialog
          message=""
          busy={saving}
          error={serverError}
          onConfirm={() => confirmCheck()}
          onCancel={cancelConfirmation}
        >
          <p>{confirmationMessage}</p>
        </ConfirmationDialog>
      )}
    </section>
  )
}
