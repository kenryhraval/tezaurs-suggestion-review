import { useEffect, useState, type ChangeEvent, type FocusEvent } from 'react'
import { getTezaursCandidates, getTezaursEntry } from '../api'
import type { TezaursEntryCandidate, TezaursEntryDetails, TezaursStatus } from '../types'
import { ConfirmationDialog } from './ConfirmationDialog'
import { TezaursEntryLink } from './TezaursEntryLink'

type Props = {
  reviewId: string
  status: TezaursStatus
  matchedEntryId: number | null
  caseClosed?: boolean
  onSave: (status: TezaursStatus, entryId: number | null) => Promise<boolean>
}

const NOT_FOUND = 'not-found'

function initialSelection(status: TezaursStatus, matchedEntryId: number | null) {
  if (status === 'NOT_FOUND') return NOT_FOUND
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
  const [expanded, setExpanded] = useState(
    status === 'NOT_CHECKED' || status === 'FOUND',
  )
  const selectedEntryId = Number.isInteger(Number(selection)) && selection !== ''
    ? Number(selection)
    : null
  const selectedCandidate = candidates.find((candidate) => candidate.id === selectedEntryId)
    ?? (customSelection?.id === selectedEntryId ? customSelection : undefined)
  const visibleCandidates = selectedCandidate
    && !candidates.some((candidate) => candidate.id === selectedCandidate.id)
    ? [...candidates, selectedCandidate]
    : candidates
  const searchTerm = query.trim()
  const displayedCandidates = searchTerm === '' ? visibleCandidates : searchResults
  const previewCandidate = displayedCandidates
    .find((candidate) => candidate.id === previewEntryId)
    ?? (selectedCandidate?.id === previewEntryId ? selectedCandidate : undefined)
  const confirmed = status === 'NOT_FOUND'
    || status === 'MEANING_ALREADY_EXISTS'
    || status === 'MEANING_NEEDS_EDITING'
    || status === 'MEANING_NOT_FOUND'
  const hasChanges = pendingStatus !== status || selectedEntryId !== matchedEntryId
    || pendingSenseEdit !== editExistingSenses

  useEffect(() => {
    let active = true
    const selectedId = matchedEntryId

    if (selectedId === null) return

    async function loadSelectedEntry(entryId: number) {
      try {
        const loaded = await getTezaursEntry(entryId)
        if (active) setCustomSelection(asCandidate(loaded))
      } catch (cause) {
        if (active) {
          setServerError(cause instanceof Error
            ? cause.message
            : 'Neizdevās pārbaudīt Tēzauru.')
        }
      } finally {
        if (active) setLoading(false)
      }
    }

    void loadSelectedEntry(selectedId)
    return () => {
      active = false
    }
  }, [matchedEntryId])

  useEffect(() => {
    const term = query.trim()
    if (!dropdownOpen || term.length === 1) return

    let active = true
    const timeout = window.setTimeout(async () => {
      try {
        const loaded = await getTezaursCandidates(reviewId, term || undefined)
        if (active) {
          if (term === '') setCandidates(loaded)
          else setSearchResults(loaded)
        }
      } catch (cause) {
        if (active) {
          setSearchError(cause instanceof Error
            ? cause.message
            : 'Neizdevās pārbaudīt Tēzauru.')
        }
      } finally {
        if (active) setSearching(false)
      }
    }, term === '' ? 0 : 300)

    return () => {
      active = false
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

  function closeDropdownOnBlur(event: FocusEvent<HTMLDivElement>) {
    if (!event.currentTarget.contains(event.relatedTarget)) closeDropdown()
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
    if (status === 'NOT_FOUND' && selection === NOT_FOUND) return
    setConfirmationError('')
    setConfirmation('NOT_FOUND')
  }

  function chooseMeaning(nextStatus: 'MEANING_ALREADY_EXISTS' | 'MEANING_NOT_FOUND') {
    if (nextStatus === 'MEANING_NOT_FOUND' && nextStatus === status && selectedEntryId === matchedEntryId) return
    setPendingSenseEdit(nextStatus === 'MEANING_ALREADY_EXISTS' && selectedEntryId === matchedEntryId && editExistingSenses)
    setConfirmationError('')
    setConfirmation(nextStatus)
  }

  async function confirmCheck() {
    if (!confirmation || saving) return
    setSaving(true)
    try {
      let nextStatus = confirmation
      if (confirmation === 'MEANING_ALREADY_EXISTS' && pendingSenseEdit) {
        nextStatus = 'MEANING_NEEDS_EDITING'
      }
      if (await onSave(nextStatus, confirmation === 'NOT_FOUND' ? null : selectedEntryId)) {
        setPendingSenseEdit(nextStatus === 'MEANING_NEEDS_EDITING')
        setPendingStatus(nextStatus)
        if (confirmation === 'NOT_FOUND') setSelection(NOT_FOUND)
        setConfirmation(null)
        closeDropdown()
        setExpanded(false)
      } else {
        setConfirmationError('Neizdevās saglabāt izvēli. Mēģiniet vēlreiz.')
      }
    } finally {
      setSaving(false)
    }
  }

  let confirmationMessage = 'Šķirkļa nav Tēzaurā. Apstiprinot tiks izveidots šķirkļa melnraksts.'
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
      <details open={expanded} onToggle={(event) => {
        setExpanded(event.currentTarget.open)
        if (!event.currentTarget.open) closeDropdown()
      }}>
        <summary className={!expanded && confirmed && !hasChanges ? 'confirmed' : undefined}>
          <strong>2. Pārbaude Tēzaurā</strong>
        </summary>
        <p className="muted">Izvēlieties atbilstošo šķirkli vai norādiet, ka tāda nav.</p>

        {serverError && <p className="error">{serverError}</p>}
        {loading && <p className="muted">Ielādē izvēlēto šķirkli...</p>}

        <div
          className="tezaurs-dropdown"
          onBlur={closeDropdownOnBlur}
          onKeyDown={(event) => {
            if (event.key === 'Escape') closeDropdown()
          }}
        >
          <div className="tezaurs-dropdown-control">
            <button
              aria-expanded={dropdownOpen}
              aria-label="Tēzaura šķirklis"
              className="tezaurs-dropdown-toggle"
              disabled={loading || saving}
              onClick={() => {
                if (dropdownOpen) closeDropdown()
                else openDropdown()
              }}
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

      </details>
      {!expanded && confirmed && !hasChanges && (
        <p className="muted">
          {status === 'NOT_FOUND' ? 'Šķirkļa nav Tēzaurā.'
            : status === 'MEANING_NEEDS_EDITING' ? 'Nozīme jau ir Tēzaurā; esošā nozīme jāprecizē.'
              : status === 'MEANING_ALREADY_EXISTS' ? 'Šķirklis un ieteiktā nozīme jau ir Tēzaurā.'
              : 'Šķirklis ir Tēzaurā; jāpievieno jauna nozīme.'}
        </p>
      )}
      {confirmation && (
        <ConfirmationDialog
          message={confirmation === 'MEANING_ALREADY_EXISTS' ? '' : confirmationMessage}
          busy={saving}
          error={confirmationError}
          onConfirm={() => void confirmCheck()}
          onCancel={() => {
            setPendingSenseEdit(selectedEntryId === matchedEntryId && editExistingSenses)
            setConfirmation(null)
          }}
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
