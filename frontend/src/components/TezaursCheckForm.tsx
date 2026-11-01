import { useEffect, useState, type ChangeEvent, type FocusEvent, type KeyboardEvent, type SyntheticEvent } from 'react'
import { getTezaursCandidates, getTezaursEntry } from '../api/client'
import type { TezaursEntryCandidate, TezaursEntryDetails, TezaursStatus } from '../api/types'
import { ConfirmationDialog } from './ConfirmationDialog'
import { TezaursEntryLink } from './TezaursEntryLink'

type Props = {
  reviewId: string
  status: TezaursStatus
  matchedEntryId: number | null
  caseClosed?: boolean
  readOnly: boolean
  defaultOpen: boolean
  onSave: (status: TezaursStatus, entryId: number | null) => Promise<boolean>
}

const NOT_FOUND = 'not-found'
const checkDescriptions = {
  NOT_CHECKED: 'Pārbaude Tēzaurā nav veikta.',
  FOUND: 'Šķirklis ir Tēzaurā; nozīme vēl nav pārbaudīta.',
  NOT_FOUND: 'Šķirkļa nav Tēzaurā.',
  MEANING_NEEDS_EDITING: 'Nozīme jau ir Tēzaurā; esošā nozīme jāprecizē.',
  MEANING_ALREADY_EXISTS: 'Šķirklis un ieteiktā nozīme jau ir Tēzaurā.',
  MEANING_NOT_FOUND: 'Šķirklis ir Tēzaurā; jāpievieno jauna nozīme.',
}

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

function EntryDetails({ candidate }: { candidate: TezaursEntryCandidate }) {
  return (
    <>
      <strong>{candidate.heading}</strong>{' '}
      <small>{candidate.humanKey}</small>
      <EntryDescription candidate={candidate} />
    </>
  )
}

export function TezaursCheckForm({
  reviewId,
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
  const [previewEntryId, setPreviewEntryId] = useState<number | null>(null)
  const [query, setQuery] = useState('')
  const [searchResults, setSearchResults] = useState<TezaursEntryCandidate[]>([])
  const [searching, setSearching] = useState(false)
  const [searchError, setSearchError] = useState('')
  const [pendingStatus, setPendingStatus] = useState<TezaursStatus>(status)
  const [pendingSenseEdit, setPendingSenseEdit] = useState(editExistingSenses)
  const [saving, setSaving] = useState(false)
  const [confirmation, setConfirmation] = useState<TezaursStatus | null>(null)
  const [confirmationError, setConfirmationError] = useState('')
  const [expanded, setExpanded] = useState(defaultOpen)
  let selectedEntryId: number | null = null
  if (selection !== '' && Number.isInteger(Number(selection))) {
    selectedEntryId = Number(selection)
  }

  let selectedCandidate = candidates.find((candidate) => candidate.id === selectedEntryId)
  if (!selectedCandidate && customSelection?.id === selectedEntryId) {
    selectedCandidate = customSelection
  }

  let visibleCandidates = candidates
  const selectionIsInResults = candidates.some((candidate) => candidate.id === selectedCandidate?.id)
  if (selectedCandidate && !selectionIsInResults) {
    visibleCandidates = [...candidates, selectedCandidate]
  }

  const searchTerm = query.trim()
  const displayedCandidates = searchTerm === '' ? visibleCandidates : searchResults
  let previewCandidate = displayedCandidates.find((candidate) => candidate.id === previewEntryId)
  if (!previewCandidate && selectedCandidate?.id === previewEntryId) {
    previewCandidate = selectedCandidate
  }
  const confirmed = status === 'NOT_FOUND'
    || status === 'MEANING_ALREADY_EXISTS'
    || status === 'MEANING_NEEDS_EDITING'
    || status === 'MEANING_NOT_FOUND'
  const hasChanges = pendingStatus !== status || selectedEntryId !== matchedEntryId
    || pendingSenseEdit !== editExistingSenses

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
    if (!dropdownOpen || term.length === 1) {
      return
    }

    let requestIsOutdated = false
    async function searchEntries() {
      try {
        const loaded = await getTezaursCandidates(reviewId, term || undefined)
        if (!requestIsOutdated) {
          if (term === '') {
            setCandidates(loaded)
          } else {
            setSearchResults(loaded)
          }
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

    const searchDelay = term === '' ? 0 : 300
    const timeout = window.setTimeout(searchEntries, searchDelay)

    return () => {
      requestIsOutdated = true
      window.clearTimeout(timeout)
    }
  }, [dropdownOpen, query, reviewId])

  function openDropdown() {
    setQuery('')
    setCandidates([])
    setSearchResults([])
    setSearchError('')
    setSearching(true)
    setPreviewEntryId(selectedEntryId)
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
    if (!event.currentTarget.contains(event.relatedTarget)) {
      closeDropdown()
    }
  }

  function changeQuery(event: ChangeEvent<HTMLInputElement>) {
    const value = event.target.value
    setQuery(value)
    setSearchResults([])
    setSearchError('')
    setPreviewEntryId(null)
    setSearching(value.trim().length >= 2)
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
    setConfirmationError('')
    setConfirmation('NOT_FOUND')
  }

  function chooseMeaning(nextStatus: 'MEANING_ALREADY_EXISTS' | 'MEANING_NOT_FOUND') {
    if (nextStatus === 'MEANING_NOT_FOUND' && nextStatus === status && selectedEntryId === matchedEntryId) {
      return
    }
    setPendingSenseEdit(nextStatus === 'MEANING_ALREADY_EXISTS' && selectedEntryId === matchedEntryId && editExistingSenses)
    setConfirmationError('')
    setConfirmation(nextStatus)
  }

  async function confirmCheck() {
    if (!confirmation || saving) {
      return
    }
    setSaving(true)
    try {
      let nextStatus = confirmation
      if (confirmation === 'MEANING_ALREADY_EXISTS' && pendingSenseEdit) {
        nextStatus = 'MEANING_NEEDS_EDITING'
      }
      const entryIdToSave = confirmation === 'NOT_FOUND' ? null : selectedEntryId
      const saved = await onSave(nextStatus, entryIdToSave)
      if (saved) {
        setPendingSenseEdit(nextStatus === 'MEANING_NEEDS_EDITING')
        setPendingStatus(nextStatus)
        if (confirmation === 'NOT_FOUND') {
          setSelection(NOT_FOUND)
        }
        setConfirmation(null)
        closeDropdown()
        setExpanded(false)
      } else {
        setConfirmationError('Neizdevās saglabāt izvēli. Mēģiniet vēlreiz.')
      }
    } catch (cause) {
      setConfirmationError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt izvēli.')
    } finally {
      setSaving(false)
    }
  }

  let confirmationMessage = 'Šķirkļa nav Tēzaurā. Apstiprinot varēsiet pārbaudīt vārda lietojumu.'
  if (confirmation === 'MEANING_ALREADY_EXISTS') {
    confirmationMessage = pendingSenseEdit
      ? 'Izskatīšana paliks statusā “Apstrādē”. Varēsiet labot esošo nozīmi melnrakstā; Tēzaura dati netiks mainīti.'
      : 'Apstiprinot izskatīšana tiks slēgta ar statusu “Jau ir”.'
  } else if (confirmation === 'MEANING_NOT_FOUND') {
    confirmationMessage = `Šķirklī “${selectedCandidate?.humanKey ?? ''}” ieteiktās nozīmes nav. Apstiprinot varēsiet pievienot jaunu nozīmi.`
  }
  if (caseClosed && (confirmation !== 'MEANING_ALREADY_EXISTS' || pendingSenseEdit)) {
    confirmationMessage += ' Izskatīšana atgriezīsies statusā “Apstrādē”.'
  }
  const nextEntryId = confirmation === 'NOT_FOUND' ? null : selectedEntryId
  if (confirmation && nextEntryId !== matchedEntryId) {
    confirmationMessage += ' Iepriekšējie melnraksti paliks saglabāti pie iepriekšējās šķirkļa izvēles.'
  }

  return (
    <section className="tezaurs-check">
      <details open={expanded} onToggle={toggleSection}>
        <summary className={!expanded && confirmed && (readOnly || !hasChanges) ? 'confirmed' : undefined}>
          <strong>2. Pārbaude Tēzaurā</strong>
        </summary>

        {serverError && <p className="error">{serverError}</p>}
        {loading && <p className="muted">Ielādē izvēlēto šķirkli...</p>}

        <fieldset className="review-controls" disabled={readOnly}>
          <p className="muted">Izvēlieties atbilstošo šķirkli vai norādiet, ka tāda nav.</p>
          <div
            className="tezaurs-dropdown"
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
                    <input autoFocus onChange={changeQuery} type="search" value={query} />
                  </label>

                  {searchTerm.length === 1 && <small>Ievadiet vēl vienu burtu.</small>}
                  {searching && <small>Meklē...</small>}
                  {searchError && <small className="error">{searchError}</small>}
                  {!searching && !searchError && searchTerm.length >= 2
                    && searchResults.length === 0 && <small>Nekas netika atrasts.</small>}
                </div>

                <div className="tezaurs-dropdown-options">
                  {displayedCandidates.map((candidate) => (
                    <div className="tezaurs-option"
                      key={candidate.id}
                      onFocus={() => setPreviewEntryId(candidate.id)}
                      onMouseEnter={() => setPreviewEntryId(candidate.id)}
                    >
                      <button aria-pressed={candidate.id === selectedEntryId}
                        onClick={() => chooseCandidate(candidate)} type="button">
                        <span className="tezaurs-option-label">
                          <strong>{candidate.heading}</strong>
                          <small>{candidate.humanKey}</small>
                        </span>
                      </button>
                      <TezaursEntryLink humanKey={candidate.humanKey} />
                    </div>
                  ))}
                  <button
                    aria-pressed={selection === NOT_FOUND}
                    className="tezaurs-dropdown-special"
                    onClick={chooseNotFound}
                    onFocus={() => setPreviewEntryId(null)}
                    onMouseEnter={() => setPreviewEntryId(null)}
                    type="button"
                  >
                    Šķirkļa nav Tēzaurā
                  </button>
                </div>

                {previewCandidate && (
                  <div className="tezaurs-dropdown-preview">
                    <EntryDetails candidate={previewCandidate} />
                  </div>
                )}
              </div>
            )}
          </div>

          {selectedEntryId !== null && selectedCandidate && (
            <div className="meaning-existence-check">
              <p><strong>
                Vai iesniegtā nozīme jau ir šķirklī “{selectedCandidate.heading}”?
              </strong></p>
              <div className="tezaurs-answer-actions">
                <button aria-pressed={pendingStatus === 'MEANING_ALREADY_EXISTS' || pendingStatus === 'MEANING_NEEDS_EDITING'} disabled={saving}
                  onClick={() => chooseMeaning('MEANING_ALREADY_EXISTS')} type="button">
                  Jā, nozīme jau ir
                </button>
                <button aria-pressed={pendingStatus === 'MEANING_NOT_FOUND'} disabled={saving}
                  onClick={() => chooseMeaning('MEANING_NOT_FOUND')} type="button">
                  Nē, tā ir jauna nozīme
                </button>
              </div>
            </div>
          )}
        </fieldset>

      </details>
      {!expanded && confirmed && (readOnly || !hasChanges) && (
        <p className="muted">
          {checkDescriptions[status]}
        </p>
      )}
      {confirmation && (
        <ConfirmationDialog
          message={confirmation === 'MEANING_ALREADY_EXISTS' ? '' : confirmationMessage}
          busy={saving}
          error={confirmationError}
          onConfirm={() => confirmCheck()}
          onCancel={cancelConfirmation}
        >
          {confirmation === 'MEANING_ALREADY_EXISTS' && <>
            <label className="checkbox-label">
              <input autoFocus type="checkbox" checked={pendingSenseEdit} disabled={saving}
                onChange={(event) => setPendingSenseEdit(event.target.checked)} />
              Vēlos labot esošo nozīmi
            </label>
            <p>{confirmationMessage}</p>
          </>}
        </ConfirmationDialog>
      )}
    </section>
  )
}
