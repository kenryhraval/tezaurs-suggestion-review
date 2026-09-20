import { suggestionStatusLabels } from '../labels'
import type { Suggestion, SuggestionStatus } from '../types'

const suggestionGroups = [
  {
    key: 'NEW',
    label: suggestionStatusLabels.NEW,
    matches: (suggestion: Suggestion) => suggestion.status === 'NEW',
  },
  {
    key: 'IN_PROGRESS',
    label: suggestionStatusLabels.IN_PROGRESS,
    matches: (suggestion: Suggestion) => suggestion.status === 'POSTPONED'
      || suggestion.status === 'NEEDS_EXPERT'
      || (suggestion.status === 'IN_PROGRESS'
        && suggestion.tezaursStatus !== 'FOUND'
        && suggestion.tezaursStatus !== 'MEANING_NOT_FOUND'),
  },
  {
    key: 'MEANING_NOT_FOUND',
    label: 'Jāpapildina',
    matches: (suggestion: Suggestion) => suggestion.status === 'IN_PROGRESS'
      && (suggestion.tezaursStatus === 'FOUND'
        || suggestion.tezaursStatus === 'MEANING_NOT_FOUND'),
  },
  {
    key: 'COMPLETED',
    label: suggestionStatusLabels.COMPLETED,
    matches: (suggestion: Suggestion) => suggestion.status === 'COMPLETED',
  },
  {
    key: 'GARBAGE',
    label: suggestionStatusLabels.GARBAGE,
    matches: (suggestion: Suggestion) => suggestion.status === 'GARBAGE',
  },
] as const

type Props = {
  suggestions: Suggestion[]
  selectedId: string | null
  loading: boolean
  onSelect: (id: string) => void
  onStatusChange: (id: string, status: SuggestionStatus) => Promise<void>
}

export function SuggestionList({
  suggestions,
  selectedId,
  loading,
  onSelect,
  onStatusChange,
}: Props) {
  return (
    <aside>
      <h2>Ieteikumi</h2>

      {loading && <p>Ielādē...</p>}
      {!loading && suggestions.length === 0 && <p>Ieteikumu vēl nav.</p>}

      {!loading && suggestions.length > 0 && (
        <div className="suggestion-groups">
          {suggestionGroups.map((group) => {
            const items = suggestions.filter(group.matches)

            return (
              <section className="suggestion-group" key={group.key}>
                <div className="suggestion-group-heading">
                  <h3>
                    {group.label}
                    <span className="suggestion-count">{items.length}</span>
                  </h3>

                  {group.key === 'COMPLETED' && (
                    <button disabled title="Eksports būs pieejams vēlāk" type="button">
                      Eksportēt
                    </button>
                  )}
                  {group.key === 'GARBAGE' && (
                    <button disabled title="Miskastes iztukšošana būs pieejama vēlāk" type="button">
                      Iztukšot
                    </button>
                  )}
                </div>

                {items.length === 0 ? (
                  <p className="empty-group">Nav ieteikumu.</p>
                ) : (
                  <ul className="suggestion-list">
                    {items.map((suggestion) => (
                      <li key={suggestion.id}>
                        <button
                          className={`suggestion-select${suggestion.id === selectedId ? ' selected' : ''}`}
                          onClick={() => onSelect(suggestion.id)}
                          type="button"
                        >
                          <strong>{suggestion.reviewedTerm ?? suggestion.submittedTerm}</strong>
                        </button>

                        {group.key === 'NEW' && (
                          <div className="suggestion-quick-actions">
                            <button
                              aria-label={`Sākt izskatīt ${suggestion.submittedTerm}`}
                              onClick={() => void onStatusChange(suggestion.id, 'IN_PROGRESS')}
                              title="Pārvietot uz Izskatīšanā"
                              type="button"
                            >
                              ✓
                            </button>
                            <button
                              aria-label={`Pārvietot ${suggestion.submittedTerm} uz miskasti`}
                              onClick={() => void onStatusChange(suggestion.id, 'GARBAGE')}
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
              </section>
            )
          })}
        </div>
      )}
    </aside>
  )
}
