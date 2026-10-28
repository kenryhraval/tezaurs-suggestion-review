import { useState, type FormEvent } from 'react'
import type { CorpusExample } from '../types'

type Props = {
  examples: CorpusExample[]
  loading: boolean
  serverError: string
  onAdd: (url: string) => Promise<boolean>
}

export function CorpusExampleLinks({ examples, loading, serverError, onAdd }: Props) {
  const [error, setError] = useState('')

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    const form = event.currentTarget
    const data = new FormData(form)
    const url = String(data.get('url')).trim()

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
      form.reset()
    }
  }

  return (
    <section className="corpus-examples">
      <h3>Pārbaude korpusā</h3>

      {(error || serverError) && <p className="error">{error || serverError}</p>}

      <p className="muted">Avota veida saglabāšana tiks pievienota vēlāk.</p>

      <form onSubmit={(event) => void handleSubmit(event)}>
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
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
