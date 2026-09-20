import { useState, type FormEvent } from 'react'
import { createSuggestion } from '../api'

function optionalText(data: FormData, name: string) {
  const value = String(data.get(name) ?? '').trim()
  return value || null
}

export function ImportPage() {
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setMessage('')
    setError('')
    const form = event.currentTarget
    const data = new FormData(form)

    try {
      const created = await createSuggestion({
        term: String(data.get('term')),
        definition: String(data.get('definition')),
        usageExample: optionalText(data, 'usageExample'),
        notes: optionalText(data, 'notes'),
        submitterName: optionalText(data, 'submitterName'),
        submitterEmail: optionalText(data, 'submitterEmail'),
      })
      setMessage(`Ieteikums “${created.submittedTerm}” importēts.`)
      form.reset()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās importēt ieteikumu.')
    }
  }

  return (
    <main className="import-page">
      <header>
        <h1>Ieteikuma manuāla importēšana</h1>
        <p>Šī palīglapa nav pieejama galvenajā navigācijā.</p>
      </header>

      {message && <p className="success">{message}</p>}
      {error && <p className="error">{error}</p>}

      <form onSubmit={handleSubmit}>
        <label>
          Vārds vai vārdkopa
          <input name="term" maxLength={255} required />
        </label>
        <label>
          Definīcija
          <textarea name="definition" maxLength={10000} required />
        </label>
        <label>
          Lietojuma piemērs
          <textarea name="usageExample" maxLength={10000} />
        </label>
        <label>
          Piezīmes
          <textarea name="notes" maxLength={10000} />
        </label>
        <label>
          Iesniedzēja vārds
          <input name="submitterName" maxLength={255} />
        </label>
        <label>
          Iesniedzēja e-pasts
          <input name="submitterEmail" type="email" maxLength={320} />
        </label>
        <button type="submit">Importēt ieteikumu</button>
      </form>

      <p><a href="/">Atpakaļ uz ieteikumu sarakstu</a></p>
    </main>
  )
}
