import { useState, type FormEvent } from 'react'
import { checkStatuses, checkStatusLabels } from '../labels'
import type { CheckStatus } from '../types'

type Props = {
  initialStatus: CheckStatus
  initialEntryId: number | null
  onSave: (status: CheckStatus, entryId: number | null) => Promise<void>
}

export function TezaursCheckForm({ initialStatus, initialEntryId, onSave }: Props) {
  const [status, setStatus] = useState(initialStatus)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const entryId = data.get('entryId')
    const parsedEntryId = status === 'FOUND' && entryId ? Number(entryId) : null

    void onSave(status, parsedEntryId)
  }

  return (
    <form onSubmit={handleSubmit}>
      <h3>Pārbaude Tēzaurā</h3>

      <label>
        Rezultāts
        <select
          value={status}
          onChange={(event) => setStatus(event.target.value as CheckStatus)}
        >
          {checkStatuses.map((value) => (
            <option key={value} value={value}>{checkStatusLabels[value]}</option>
          ))}
        </select>
      </label>

      {status === 'FOUND' && (
        <label>
          Tēzaura šķirkļa ID
          <input
            name="entryId"
            type="number"
            min="1"
            defaultValue={initialEntryId ?? ''}
            required
          />
        </label>
      )}

      <button type="submit">Saglabāt pārbaudi</button>
    </form>
  )
}
