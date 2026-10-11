import { useEffect, useState } from 'react'
import { getTezaursCandidates } from '../api'
import type { TezaursEntryCandidate, TezaursStatus } from '../types'

type Props = {
  reviewId: string
  status: TezaursStatus
  matchedEntryId: number | null
  onSave: (status: TezaursStatus, entryId: number | null) => Promise<void>
}

export function TezaursCheckForm({
  reviewId,
  status,
  matchedEntryId,
  onSave,
}: Props) {
  const [candidates, setCandidates] = useState<TezaursEntryCandidate[]>([])
  const [loading, setLoading] = useState(true)
  const [serverError, setServerError] = useState('')
  const [chosenEntryId, setChosenEntryId] = useState<number | null>(null)
  const selectedEntryId = chosenEntryId ?? matchedEntryId ?? candidates[0]?.id ?? null
  const selectedCandidate = candidates.find((candidate) => candidate.id === selectedEntryId)
  const entryExists = status === 'FOUND'
    || status === 'MEANING_FOUND'
    || status === 'MEANING_NOT_FOUND'

  useEffect(() => {
    let active = true

    async function loadCandidates() {
      try {
        const loaded = await getTezaursCandidates(reviewId)
        if (active) setCandidates(loaded)
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

    void loadCandidates()
    return () => {
      active = false
    }
  }, [reviewId])

  function confirmEntry() {
    if (selectedEntryId !== null) void onSave('FOUND', selectedEntryId)
  }

  function recordMeaning(status: 'MEANING_FOUND' | 'MEANING_NOT_FOUND') {
    if (selectedEntryId !== null) void onSave(status, selectedEntryId)
  }

  return (
    <section className="tezaurs-check">
      <h3>Pārbaude Tēzaurā</h3>
      <p className="muted">Izvēlieties tuvāko atrasto šķirkli vai norādiet, ka atbilstoša šķirkļa nav.</p>

      {serverError && <p className="error">{serverError}</p>}
      {loading && <p className="muted">Meklē Tēzaurā...</p>}

      {!loading && candidates.length === 0 && (
        <p>Neviens pietiekami līdzīgs šķirklis netika atrasts.</p>
      )}

      {candidates.length > 0 && (
        <div className="tezaurs-candidates">
          {candidates.map((candidate) => (
            <label className="tezaurs-candidate" key={candidate.id}>
              <span className="tezaurs-candidate-title">
                <input
                  checked={candidate.id === selectedEntryId}
                  name="tezaursEntry"
                  onChange={() => setChosenEntryId(candidate.id)}
                  type="radio"
                />
                <strong>{candidate.heading}</strong>
                <small>{candidate.humanKey}</small>
              </span>
              {candidate.matchedLexeme !== candidate.heading && (
                <small>Atrasts pēc leksēmas “{candidate.matchedLexeme}”</small>
              )}
              {candidate.senses.length > 0 ? (
                <ol>
                  {candidate.senses.map((sense) => (
                    <li key={sense.id}>{sense.gloss || 'Nozīmes skaidrojums nav norādīts.'}</li>
                  ))}
                </ol>
              ) : (
                <small>Šķirklim nav nozīmju skaidrojumu.</small>
              )}
            </label>
          ))}
        </div>
      )}

      <div className="tezaurs-answer-actions">
        <button disabled={selectedEntryId === null} onClick={confirmEntry} type="button">
          Šis šķirklis atbilst
        </button>
        <button onClick={() => void onSave('NOT_FOUND', null)} type="button">
          Šķirkļa nav Tēzaurā
        </button>
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
