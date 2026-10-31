import { suggestionStatusLabels } from '../labels'
import type { QueueItem, SuggestionSort, SuggestionStatus } from '../types'

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
  items: QueueItem[]
  status: SuggestionStatus
  sort: SuggestionSort
  search: string
  selectedKey: string | null
  loading: boolean
  page: number
  totalPages: number
  totalItems: number
  onSelect: (key: string) => void
  onFilterStatusChange: (status: SuggestionStatus) => void
  onSortChange: (sort: SuggestionSort) => void
  onSearchChange: (search: string) => void
  onPageChange: (page: number) => void
}

function itemKey(item: QueueItem) {
  return `${item.itemType}:${item.id}`
}

function itemTerm(item: QueueItem) {
  return item.itemType === 'SUGGESTION' ? item.submittedTerm : item.reviewedTerm
}

export function SuggestionList({
  items,
  status,
  sort,
  search,
  selectedKey,
  loading,
  page,
  totalPages,
  totalItems,
  onSelect,
  onFilterStatusChange,
  onSortChange,
  onSearchChange,
  onPageChange,
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
      {!loading && items.length === 0 && <p className="empty-group">Nav ieteikumu.</p>}

      {!loading && items.length > 0 && (
        <ul className="suggestion-list">
          {items.map((item) => (
            <li
              className={itemKey(item) === selectedKey ? 'selected' : undefined}
              key={itemKey(item)}
            >
              <button
                className="suggestion-select"
                onClick={() => onSelect(itemKey(item))}
                type="button"
              >
                <strong>{itemTerm(item)}</strong>
                {item.itemType === 'REVIEW' && (
                  <small>
                    {item.sourceCount}{' '}
                    {item.sourceCount === 1 ? 'ieteikums' : 'ieteikumi'}
                  </small>
                )}
              </button>
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
