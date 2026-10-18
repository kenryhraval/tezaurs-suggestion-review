import { suggestionStatusLabels } from '../labels'
import type { Suggestion, SuggestionSort, SuggestionStatus } from '../types'

const statuses: SuggestionStatus[] = [
  'NEW',
  'READY_FOR_REVIEW',
  'IN_PROGRESS',
  'INVENTED',
  'ALREADY_EXISTS',
  'INSUFFICIENT_DATA',
  'NEEDS_EXPERT',
  'COMPLETED',
  'GARBAGE',
]

type Props = {
  suggestions: Suggestion[]
  status: SuggestionStatus
  sort: SuggestionSort
  search: string
  selectedId: number | null
  loading: boolean
  page: number
  totalPages: number
  totalItems: number
  onSelect: (id: number) => void
  onFilterStatusChange: (status: SuggestionStatus) => void
  onSortChange: (sort: SuggestionSort) => void
  onSearchChange: (search: string) => void
  onPageChange: (page: number) => void
  onSuggestionStatusChange: (id: number, status: SuggestionStatus) => Promise<void>
}

export function SuggestionList({
  suggestions,
  status,
  sort,
  search,
  selectedId,
  loading,
  page,
  totalPages,
  totalItems,
  onSelect,
  onFilterStatusChange,
  onSortChange,
  onSearchChange,
  onPageChange,
  onSuggestionStatusChange,
}: Props) {
  return (
    <aside>
      <h2>Ieteikumi</h2>

      <div className="suggestion-filters">
        <label>
          Statuss
          <select
            onChange={(event) => onFilterStatusChange(event.target.value as SuggestionStatus)}
            value={status}
          >
            {statuses.map((value) => (
              <option key={value} value={value}>{suggestionStatusLabels[value]}</option>
            ))}
          </select>
        </label>

        <label>
          Meklēt
          <input
            onChange={(event) => onSearchChange(event.target.value)}
            placeholder="Vārds vai nozīme"
            type="search"
            value={search}
          />
        </label>

        <label>
          Kārtot
          <select
            onChange={(event) => onSortChange(event.target.value as SuggestionSort)}
            value={sort}
          >
            <option value="NEWEST">Jaunākie</option>
            <option value="OLDEST">Vecākie</option>
            {status !== 'NEW' && (
              <option value="MOST_SUGGESTIONS">Visvairāk ieteikumu</option>
            )}
          </select>
        </label>
      </div>

      <div className="suggestion-group-heading">
        <h3>
          {suggestionStatusLabels[status]}
          <span className="suggestion-count">{totalItems}</span>
        </h3>
      </div>

      {loading && <p>Ielādē...</p>}
      {!loading && suggestions.length === 0 && <p className="empty-group">Nav ieteikumu.</p>}

      {!loading && suggestions.length > 0 && (
        <ul className="suggestion-list">
          {suggestions.map((suggestion) => (
            <li
              className={suggestion.id === selectedId ? 'selected' : undefined}
              key={suggestion.id}
            >
              <button
                className="suggestion-select"
                onClick={() => onSelect(suggestion.id)}
                type="button"
              >
                <strong>{suggestion.submittedTerm}</strong>
                <span className="suggestion-definition">
                  {suggestion.submittedDefinition}
                </span>
                {status !== 'NEW' && (
                  <small>
                    {suggestion.sourceCount}{' '}
                    {suggestion.sourceCount === 1 ? 'ieteikums' : 'ieteikumi'}
                  </small>
                )}
              </button>

              {status === 'NEW' && (
                <div className="suggestion-quick-actions">
                  <button
                    aria-label={`Sākt izskatīt ${suggestion.submittedTerm}`}
                    onClick={() => void onSuggestionStatusChange(
                      suggestion.id, 'READY_FOR_REVIEW',
                    )}
                    title="Pārvietot uz Skatāms"
                    type="button"
                  >
                    ✓
                  </button>
                  <button
                    aria-label={`Pārvietot ${suggestion.submittedTerm} uz miskasti`}
                    onClick={() => void onSuggestionStatusChange(suggestion.id, 'GARBAGE')}
                    title="Pārvietot uz Miskasti"
                    type="button"
                  >
                    Miskastē
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {totalPages > 1 && (
        <div className="suggestion-pagination">
          <button disabled={page === 0} onClick={() => onPageChange(page - 1)} type="button">
            Iepriekšējā
          </button>
          <span>{page + 1} / {totalPages}</span>
          <button
            disabled={page + 1 >= totalPages}
            onClick={() => onPageChange(page + 1)}
            type="button"
          >
            Nākamā
          </button>
        </div>
      )}
    </aside>
  )
}
