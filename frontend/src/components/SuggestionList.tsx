import { suggestionStatusLabels } from '../labels'
import type { Suggestion } from '../types'

type Props = {
  suggestions: Suggestion[]
  selectedId: string | null
  loading: boolean
  onSelect: (id: string) => void
}

export function SuggestionList({ suggestions, selectedId, loading, onSelect }: Props) {
  return (
    <aside>
      <h2>Ieteikumi</h2>

      {loading && <p>Ielādē...</p>}
      {!loading && suggestions.length === 0 && <p>Ieteikumu vēl nav.</p>}

      <ul className="suggestion-list">
        {suggestions.map((suggestion) => (
          <li key={suggestion.id}>
            <button
              className={suggestion.id === selectedId ? 'selected' : ''}
              onClick={() => onSelect(suggestion.id)}
              type="button"
            >
              <strong>{suggestion.reviewedTerm ?? suggestion.submittedTerm}</strong>
              <span>{suggestionStatusLabels[suggestion.status]}</span>
            </button>
          </li>
        ))}
      </ul>
    </aside>
  )
}
