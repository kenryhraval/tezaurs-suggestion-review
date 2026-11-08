import { useEffect, useRef, useState, type ChangeEvent, type FocusEvent, type KeyboardEvent, type SyntheticEvent } from 'react'
import { getTezaursCandidates, getTezaursEntry } from '../api/client'
import type { TezaursEntryCandidate, TezaursEntryDetails, TezaursStatus } from '../api/types'
import { ConfirmationDialog } from './ConfirmationDialog'
import { TezaursEntryLink } from './TezaursEntryLink'

type Props = {
  reviewId: string
  reviewedTerm: string
  status: TezaursStatus
  matchedEntryId: number | null
  caseClosed?: boolean
  readOnly: boolean
  defaultOpen: boolean
  onSave: (status: TezaursStatus, entryId: number | null) => Promise<boolean>
}

const NOT_FOUND = 'not-found'

function initialSelection(status: TezaursStatus, matchedEntryId: number | null) {
  if (status === 'NOT_FOUND') {
    return NOT_FOUND
  }
  return matchedEntryId === null ? '' : String(matchedEntryId)
}

function asCandidate(entry: TezaursEntryDetails): TezaursEntryCandidate {
  return {
    id: entry.id,
    heading: entry.heading,
    humanKey: entry.humanKey,
    senses: entry.senses.map((sense) => ({
      id: sense.id,
      parentSenseId: sense.parentSenseId,
      gloss: sense.gloss,
      orderNo: sense.orderNo,
    })),
  }
}

function EntryDescription({ candidate }: { candidate: TezaursEntryCandidate }) {
  return (
    <>
      {candidate.senses.length > 0 ? (
        <ol>
          {candidate.senses.map((sense) => (
            <li key={sense.id}>{sense.gloss || 'Nozīmes skaidrojums nav norādīts.'}</li>
          ))}
        </ol>
      ) : (
        <small>Šķirklim nav nozīmju skaidrojumu.</small>
      )}
    </>
  )
}

export function TezaursCheckForm({
  reviewId,
  reviewedTerm,
  status,
  matchedEntryId,
  caseClosed = false,
  readOnly,
  defaultOpen,
  onSave,
}: Props) {
  const editExistingSenses = status === 'MEANING_NEEDS_EDITING'
  const [candidates, setCandidates] = useState<TezaursEntryCandidate[]>([])
  const [loading, setLoading] = useState(matchedEntryId !== null)
  const [serverError, setServerError] = useState('')
  const [selection, setSelection] = useState(() => initialSelection(status, matchedEntryId))
  const [customSelection, setCustomSelection] = useState<TezaursEntryCandidate | null>(null)
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const [expandedEntryIds, setExpandedEntryIds] = useState<number[]>([])
  const [query, setQuery] = useState(reviewedTerm)
  const [searching, setSearching] = useState(false)
  const [searchError, setSearchError] = useState('')
  const [pendingStatus, setPendingStatus] = useState<TezaursStatus>(status)
  const [pendingSenseEdit, setPendingSenseEdit] = useState(editExistingSenses)
  const [saving, setSaving] = useState(false)
  const [confirmation, setConfirmation] = useState<'MEANING_ALREADY_EXISTS' | null>(null)
  const [confirmationError, setConfirmationError] = useState('')
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
  const confirmed = status === 'NOT_FOUND'
    || status === 'MEANING_ALREADY_EXISTS'
    || status === 'MEANING_NEEDS_EDITING'
    || status === 'MEANING_NOT_FOUND'
  const hasChanges = pendingStatus !== status || selectedEntryId !== matchedEntryId
    || pendingSenseEdit !== editExistingSenses
  const canCollapse = readOnly || (confirmed && !hasChanges)
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
          setCustomSelection(asCandidate(loaded))
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
        const loaded = await getTezaursCandidates(reviewId, term)
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
  }, [dropdownOpen, query, reviewId, reviewedTerm])

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
    setExpandedEntryIds(selectedEntryId === null ? [] : [selectedEntryId])
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
    setPendingSenseEdit(selectedEntryId === matchedEntryId && editExistingSenses)
    setConfirmation(null)
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
    setExpandedEntryIds([])
    setSearching(value.trim() !== '')
  }

  function showCandidateMeanings(entryId: number) {
    setExpandedEntryIds((current) => current.includes(entryId) ? current : [...current, entryId])
  }

  function chooseCandidate(candidate: TezaursEntryCandidate) {
    if (candidate.id !== selectedEntryId) {
      setPendingStatus('FOUND')
      setPendingSenseEdit(false)
    }
    setCustomSelection(candidate)
    setSelection(String(candidate.id))
    closeDropdown()
  }

  function chooseNotFound() {
    closeDropdown()
    if (status === 'NOT_FOUND' && selection === NOT_FOUND) {
      return
    }
    saveCheck('NOT_FOUND', null)
  }

  function chooseMeaning(nextStatus: 'MEANING_ALREADY_EXISTS' | 'MEANING_NOT_FOUND') {
    if (nextStatus === 'MEANING_NOT_FOUND' && nextStatus === status && selectedEntryId === matchedEntryId) {
      return
    }
    if (nextStatus === 'MEANING_NOT_FOUND') {
      saveCheck(nextStatus, selectedEntryId)
      return
    }
    setPendingSenseEdit(nextStatus === 'MEANING_ALREADY_EXISTS' && selectedEntryId === matchedEntryId && editExistingSenses)
    setConfirmationError('')
    setConfirmation(nextStatus)
  }

  async function saveCheck(nextStatus: TezaursStatus, entryId: number | null) {
    if (saving) {
      return
    }
    setSaving(true)
    setServerError('')
    setConfirmationError('')
    try {
      const saved = await onSave(nextStatus, entryId)
      if (saved) {
        setPendingSenseEdit(nextStatus === 'MEANING_NEEDS_EDITING')
        setPendingStatus(nextStatus)
        if (nextStatus === 'NOT_FOUND') {
          setSelection(NOT_FOUND)
        }
        setConfirmation(null)
        closeDropdown()
        setExpanded(false)
      } else {
        setServerError('Neizdevās saglabāt izvēli. Mēģiniet vēlreiz.')
        setConfirmationError('Neizdevās saglabāt izvēli. Mēģiniet vēlreiz.')
      }
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : 'Neizdevās saglabāt izvēli.'
      setServerError(message)
      setConfirmationError(message)
    } finally {
      setSaving(false)
    }
  }

  function confirmCheck() {
    if (!confirmation) {
      return
    }
    const nextStatus = pendingSenseEdit ? 'MEANING_NEEDS_EDITING' : confirmation
    saveCheck(nextStatus, selectedEntryId)
  }

  let confirmationMessage = pendingSenseEdit
    ? 'Izskatīšana paliks statusā “Apstrādē”. Varēsiet labot esošo nozīmi melnrakstā; Tēzaura dati netiks mainīti.'
    : 'Apstiprinot izskatīšana tiks slēgta ar statusu “Jau ir”.'
  if (caseClosed && pendingSenseEdit) {
    confirmationMessage += ' Izskatīšana atgriezīsies statusā “Apstrādē”.'
  }
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

        {serverError && <p className="error">{serverError}</p>}
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
                          onFocus={() => showCandidateMeanings(candidate.id)}
                          onMouseEnter={() => showCandidateMeanings(candidate.id)}
                          onClick={() => chooseCandidate(candidate)} type="button">
                          <span className="tezaurs-option-label">
                            <strong>{candidate.heading}</strong>
                            <small>{candidate.humanKey}</small>
                          </span>
                        </button>
                        <TezaursEntryLink humanKey={candidate.humanKey} />
                      </div>
                      {expandedEntryIds.includes(candidate.id) && (
                        <div className="tezaurs-dropdown-preview">
                          <EntryDescription candidate={candidate} />
                        </div>
                      )}
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
                <button className="btn btn-sm btn-outline-secondary" aria-pressed={pendingStatus === 'MEANING_ALREADY_EXISTS' || pendingStatus === 'MEANING_NEEDS_EDITING'} disabled={saving}
                  onClick={() => chooseMeaning('MEANING_ALREADY_EXISTS')} type="button">
                  Jā, nozīme jau ir
                </button>
                <button className="btn btn-sm btn-outline-secondary" aria-pressed={pendingStatus === 'MEANING_NOT_FOUND'} disabled={saving}
                  onClick={() => chooseMeaning('MEANING_NOT_FOUND')} type="button">
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
          error={confirmationError}
          onConfirm={() => confirmCheck()}
          onCancel={cancelConfirmation}
        >
          <>
            <label className="checkbox-label">
              <input type="checkbox" checked={pendingSenseEdit} disabled={saving}
                onChange={(event) => setPendingSenseEdit(event.target.checked)} />
              Vēlos labot esošo nozīmi
            </label>
            <p>{confirmationMessage}</p>
          </>
        </ConfirmationDialog>
      )}
    </section>
  )
}
