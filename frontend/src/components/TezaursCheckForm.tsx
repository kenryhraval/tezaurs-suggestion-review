import { useState, type FormEvent } from 'react'
import type { TezaursStatus } from '../types'

type Props = {
  status: TezaursStatus
  matchedEntryId: number | null
  onSave: (status: TezaursStatus, entryId: number | null) => Promise<void>
}

type Answer = '' | 'YES' | 'NO'

export function TezaursCheckForm({ status, matchedEntryId, onSave }: Props) {
  const [entryId, setEntryId] = useState(matchedEntryId?.toString() ?? '')
  const [error, setError] = useState('')
  const entryAnswer: Answer = status === 'NOT_CHECKED'
    ? ''
    : status === 'NOT_FOUND' ? 'NO' : 'YES'
  const meaningAnswer: Answer = status === 'MEANING_FOUND'
    ? 'YES'
    : status === 'MEANING_NOT_FOUND' ? 'NO' : ''

  function parseEntryId() {
    const parsedEntryId = entryId.trim() === '' ? null : Number(entryId)
    if (parsedEntryId !== null
      && (!Number.isInteger(parsedEntryId) || parsedEntryId < 1)) {
      setError('Ievadiet Tēzaura šķirkļa ID.')
      return undefined
    }

    setError('')
    return parsedEntryId
  }

  function saveFoundEntry(nextMeaningAnswer: Answer) {
    const parsedEntryId = parseEntryId()
    if (parsedEntryId === undefined) return

    const nextStatus: TezaursStatus = nextMeaningAnswer === 'YES'
      ? 'MEANING_FOUND'
      : nextMeaningAnswer === 'NO' ? 'MEANING_NOT_FOUND' : 'FOUND'

    void onSave(nextStatus, parsedEntryId)
  }

  function handleEntryAnswer(nextAnswer: Answer) {
    setError('')

    if (nextAnswer === '') {
      setEntryId('')
      void onSave('NOT_CHECKED', null)
      return
    }
    if (nextAnswer === 'NO') {
      setEntryId('')
      void onSave('NOT_FOUND', null)
      return
    }
    saveFoundEntry('')
  }

  function handleEntryIdSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    saveFoundEntry(meaningAnswer)
  }

  return (
    <section className="tezaurs-check">
      <h3>Pārbaude Tēzaurā</h3>

      {error && <p className="error">{error}</p>}

      <label>
        Vai šķirklis ir Tēzaurā?
        <select
          value={entryAnswer}
          onChange={(event) => handleEntryAnswer(event.target.value as Answer)}
        >
          <option value="">Nav pārbaudīts</option>
          <option value="YES">Ir</option>
          <option value="NO">Nav</option>
        </select>
      </label>

      {entryAnswer === 'YES' && (
        <>
          <form onSubmit={handleEntryIdSubmit}>
            <label>
              Tēzaura šķirkļa ID
              <input
                name="entryId"
                type="number"
                min="1"
                value={entryId}
                onChange={(event) => setEntryId(event.target.value)}
              />
            </label>
          </form>

          <label>
            Vai iesniegtā nozīme jau ir šķirklī?
            <select
              value={meaningAnswer}
              onChange={(event) => {
                const nextAnswer = event.target.value as Answer
                saveFoundEntry(nextAnswer)
              }}
            >
              <option value="">Nav pārbaudīts</option>
              <option value="YES">Ir</option>
              <option value="NO">Nav</option>
            </select>
          </label>
        </>
      )}
    </section>
  )
}
