import { useState, type FormEvent } from 'react'
import type { CorpusExample } from '../types'

const sources = [
  { value: 'KORPUSS_LV', label: 'Korpuss.lv' },
  { value: 'INTERNET', label: 'Internets' },
  { value: 'PERIODIKA_LV', label: 'Periodika.lv' },
  { value: 'TERMINI_GOV_LV', label: 'Termini.gov.lv' },
  { value: 'TEZAURS_LV', label: 'Tēzaurs.lv' },
] as const

type Source = typeof sources[number]['value']

type Props = {
  examples: CorpusExample[]
  loading: boolean
  serverError: string
  onAdd: (url: string) => Promise<boolean>
}

export function CorpusExampleLinks({ examples, loading, serverError, onAdd }: Props) {
  const [error, setError] = useState('')
  const [sourceByUrl, setSourceByUrl] = useState<Record<string, Source>>({})

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    const form = event.currentTarget
    const data = new FormData(form)
    const url = String(data.get('url')).trim()
    const source = String(data.get('source')) as Source

    try {
      const parsedUrl = new URL(url)
      if (parsedUrl.protocol !== 'http:' && parsedUrl.protocol !== 'https:') {
        throw new Error('Unsupported protocol')
      }
    } catch {
      setError('Ievadiet derīgu HTTP vai HTTPS saiti.')
      return
    }

    if (examples.some((example) => example.url === url)) {
      setError('Šī saite jau ir pievienota.')
      return
    }

    if (await onAdd(url)) {
      setSourceByUrl((current) => ({ ...current, [url]: source }))
      form.reset()
    }
  }

  return (
    <section className="corpus-examples">
      <h3>Pārbaude korpusā</h3>

      {(error || serverError) && <p className="error">{error || serverError}</p>}

      <form onSubmit={(event) => void handleSubmit(event)}>
        <label>
          <span className="visually-hidden">Avots</span>
          <select aria-label="Avots" defaultValue="KORPUSS_LV" name="source">
            {sources.map((source) => (
              <option key={source.value} value={source.value}>{source.label}</option>
            ))}
          </select>
        </label>
        <label>
          <span className="visually-hidden">Korpusa piemēra saite</span>
          <input
            name="url"
            type="url"
            maxLength={2048}
            placeholder="Piemēra saite"
            required
          />
        </label>
      </form>

      {loading && <p className="muted">Ielādē piemērus...</p>}

      {examples.length > 0 && (
        <ul>
          {examples.map((example) => (
            <li key={example.id}>
              <a href={example.url} rel="noreferrer" target="_blank">{example.url}</a>
              {sourceByUrl[example.url] && (
                <small>{sources.find((source) => source.value === sourceByUrl[example.url])?.label}</small>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
