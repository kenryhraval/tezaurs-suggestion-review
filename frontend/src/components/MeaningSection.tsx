import type { FormEvent } from 'react'
import { meaningOriginLabels } from '../labels'
import type { Meaning } from '../types'

type Props = {
  current: Meaning | undefined
  history: Meaning[]
  editingAvailable: boolean
  loading: boolean
  error: string
  onRevise: (gloss: string) => Promise<void>
}

export function MeaningSection({
  current,
  history,
  editingAvailable,
  loading,
  error,
  onRevise,
}: Props) {
  function handleRevision(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    void onRevise(String(data.get('gloss')))
  }

  if (!editingAvailable && !error) return null

  return (
    <section className="meaning-section">
      {error && <p className="error">{error}</p>}
      {loading && editingAvailable && <p>Ielādē nozīmi...</p>}
      {!loading && editingAvailable && !current && <p>Nozīme nav pieejama.</p>}

      {editingAvailable && current && (
        <div className="meaning-item">
          <h3>Pašreizējā nozīme</h3>
          <form onSubmit={handleRevision}>
            <label>
              <span className="visually-hidden">Pašreizējā nozīme</span>
              <textarea
                key={current.id}
                name="gloss"
                defaultValue={current.gloss}
                maxLength={10000}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' && !event.shiftKey) {
                    event.preventDefault()
                    event.currentTarget.form?.requestSubmit()
                  }
                }}
                required
              />
            </label>
          </form>

          {history.length > 0 && (
            <details className="meaning-history">
              <summary>Iepriekšējās versijas ({history.length})</summary>
              <ol>
                {history.map((version) => (
                  <li key={version.id}>
                    <p>{version.gloss}</p>
                    <small>
                      {version.origin !== 'SUBMITTER'
                        && `${meaningOriginLabels[version.origin]} · `}
                      {new Date(version.createdAt).toLocaleString('lv-LV')}
                    </small>
                  </li>
                ))}
              </ol>
            </details>
          )}
        </div>
      )}
    </section>
  )
}
