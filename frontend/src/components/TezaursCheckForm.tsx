import { useEffect, useState, type ChangeEvent, type FocusEvent } from 'react'
import { getTezaursCandidates, getTezaursEntry } from '../api'
import type { TezaursEntryCandidate, TezaursEntryDetails, TezaursStatus } from '../types'

type Props = {
  reviewId: string
  status: TezaursStatus
  matchedEntryId: number | null
  onSave: (status: TezaursStatus, entryId: number | null) => Promise<void>
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
  onSave,
}: Props) {
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
  const entryExists = status === 'FOUND'
    || status === 'MEANING_FOUND'
    || status === 'MEANING_NOT_FOUND'

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
    setPreviewEntryId(null)
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
    setCustomSelection(candidate)
    setSelection(String(candidate.id))
    closeDropdown()
    void onSave('FOUND', candidate.id)
  }

  function chooseNotFound() {
    setSelection(NOT_FOUND)
    closeDropdown()
    void onSave('NOT_FOUND', null)
  }

  function recordMeaning(status: 'MEANING_FOUND' | 'MEANING_NOT_FOUND') {
    if (selectedEntryId !== null) void onSave(status, selectedEntryId)
  }

  return (
    <section className="tezaurs-check">
      <h3>Pārbaude Tēzaurā</h3>
      <p className="muted">Izvēlieties atbilstošo šķirkli vai norādiet, ka tāda nav.</p>

      {serverError && <p className="error">{serverError}</p>}
      {loading && <p className="muted">Ielādē izvēlēto šķirkli...</p>}

      <span>Tēzaura šķirklis</span>
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
            disabled={loading}
            onClick={() => {
              if (dropdownOpen) closeDropdown()
              else openDropdown()
            }}
            type="button"
          >
            <span>
              {selectedCandidate
                ? <><strong>{selectedCandidate.heading}</strong>{' '}
                  <small>{selectedCandidate.humanKey}</small></>
                : selection === NOT_FOUND ? 'Šķirkļa nav Tēzaurā' : 'Izvēlieties...'}
            </span>
            <span aria-hidden="true">▾</span>
          </button>

          {selectedCandidate && (
            <div className="tezaurs-dropdown-selected-details">
              <EntryDescription candidate={selectedCandidate} />
            </div>
          )}
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
                <button
                  aria-pressed={candidate.id === selectedEntryId}
                  key={candidate.id}
                  onClick={() => chooseCandidate(candidate)}
                  onFocus={() => setPreviewEntryId(candidate.id)}
                  onMouseEnter={() => setPreviewEntryId(candidate.id)}
                  type="button"
                >
                  <strong>{candidate.heading}</strong>
                  <small>{candidate.humanKey}</small>
                </button>
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

            {previewCandidate && previewCandidate.id !== selectedEntryId && (
              <div className="tezaurs-dropdown-preview">
                <EntryDetails candidate={previewCandidate} />
              </div>
            )}
          </div>
        )}
      </div>

      {entryExists && selectedCandidate && (
        <div className="meaning-existence-check">
          <p><strong>Vai iesniegtā nozīme jau ir šķirklī “{selectedCandidate.heading}”?</strong></p>
          <div className="tezaurs-answer-actions">
            <button onClick={() => recordMeaning('MEANING_FOUND')} type="button">
              Jā, nozīme jau ir
            </button>
            <button onClick={() => recordMeaning('MEANING_NOT_FOUND')} type="button">
              Nē, tā ir jauna nozīme
            </button>
          </div>
        </div>
      )}

    </section>
  )
}
