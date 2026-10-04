import { suggestionStatusLabels } from '../labels'
import type { Suggestion, SuggestionStatus } from '../types'

const suggestionGroups: SuggestionStatus[] = [
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
  selectedId: number | null
  loading: boolean
  onSelect: (id: number) => void
  onStatusChange: (id: number, status: SuggestionStatus) => Promise<void>
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
            const items = suggestions.filter((suggestion) => suggestion.status === group)

            return (
              <section className="suggestion-group" key={group}>
                <div className="suggestion-group-heading">
                  <h3>
                    {suggestionStatusLabels[group]}
                    <span className="suggestion-count">{items.length}</span>
                  </h3>

                  {group === 'COMPLETED' && (
                    <button disabled title="Eksports būs pieejams vēlāk" type="button">
                      Eksportēt
                    </button>
                  )}
                  {group === 'GARBAGE' && (
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
                          <strong>{suggestion.submittedTerm}</strong>
                        </button>

                        {group === 'NEW' && (
                          <div className="suggestion-quick-actions">
                            <button
                              aria-label={`Sākt izskatīt ${suggestion.submittedTerm}`}
                              onClick={() => void onStatusChange(suggestion.id, 'READY_FOR_REVIEW')}
                              title="Pārvietot uz Skatāms"
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
