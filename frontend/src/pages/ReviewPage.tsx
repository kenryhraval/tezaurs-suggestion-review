import { useEffect, useState } from 'react'
import { changeSuggestionStatus, getSuggestions } from '../api'
import { SuggestionDetails } from '../components/SuggestionDetails'
import { SuggestionList } from '../components/SuggestionList'
import type { Suggestion, SuggestionStatus } from '../types'

export function ReviewPage() {
  const [suggestions, setSuggestions] = useState<Suggestion[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const selected = suggestions.find((suggestion) => suggestion.id === selectedId)

  useEffect(() => {
    async function loadSuggestions() {
      try {
        const items = await getSuggestions()
        setSuggestions(items)
        setSelectedId(items[0]?.id ?? null)
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : 'Neizdevās ielādēt ieteikumus.')
      } finally {
        setLoading(false)
      }
    }

    void loadSuggestions()
  }, [])

  function updateSuggestion(updated: Suggestion) {
    setSuggestions((items) =>
      items.map((item) => (item.id === updated.id ? updated : item)),
    )
  }

  async function changeStatus(id: string, status: SuggestionStatus) {
    setError('')
    try {
      updateSuggestion(await changeSuggestionStatus(id, status))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās mainīt statusu.')
    }
  }

  return (
    <main>
      <header>
        <h1>Tēzaura ieteikumu pārskatīšana</h1>
        <p>Ieteikumu sākotnējai izvērtēšanai.</p>
      </header>

      {error && <p className="error">{error}</p>}

      <div className="layout">
        <SuggestionList
          suggestions={suggestions}
          selectedId={selectedId}
          loading={loading}
          onSelect={setSelectedId}
          onStatusChange={changeStatus}
        />

        {!selected && !loading && (
          <section className="details">
            <p>Izvēlieties ieteikumu.</p>
          </section>
        )}

        {selected && (
          <SuggestionDetails
            key={selected.id}
            suggestion={selected}
            onUpdated={updateSuggestion}
          />
        )}
      </div>
    </main>
  )
}
