import { reviewStatusLabels } from '../review/statuses'
import type { QueueItem, SuggestionSort, ReviewStatus } from '../api/types'

const statuses: ReviewStatus[] = [
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
  status: ReviewStatus
  sort: SuggestionSort
  selectedKey: string | null
  loading: boolean
  page: number
  totalPages: number
  totalItems: number
  onSelect: (key: string) => void
  onFilterStatusChange: (status: ReviewStatus) => void
  onSortChange: (sort: SuggestionSort) => void
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
  selectedKey,
  loading,
  page,
  totalPages,
  totalItems,
  onSelect,
  onFilterStatusChange,
  onSortChange,
  onPageChange,
}: Props) {
  return (
    <aside aria-label="Ieteikumu saraksts">
      <div className="suggestion-filters">
        <select className="custom-select custom-select-sm" aria-label="Statuss"
          onChange={(event) => onFilterStatusChange(event.target.value as ReviewStatus)}
          value={status}
        >
          {statuses.map((value) => (
            <option key={value} value={value}>{reviewStatusLabels[value]}</option>
          ))}
        </select>
        <select className="custom-select custom-select-sm" aria-label="Kārtot"
          onChange={(event) => onSortChange(event.target.value as SuggestionSort)}
          value={sort}
        >
          <option value="NEWEST">Jaunākie</option>
          <option value="OLDEST">Vecākie</option>
          {status !== 'NEW' && (
            <option value="MOST_SUGGESTIONS">Visvairāk ieteikumu</option>
          )}
        </select>
      </div>

      <div className="suggestion-group-heading">
        <h3>
          {reviewStatusLabels[status]}
          <span className="suggestion-count">{totalItems}</span>
        </h3>
      </div>

      {loading && <p>Ielādē...</p>}
      {!loading && items.length === 0 && <p className="empty-group">Nav ieteikumu.</p>}

      {!loading && items.length > 0 && (
        <ul className="list-group suggestion-list">
          {items.map((item) => (
            <li
              className="list-group-item p-0 overflow-hidden"
              key={itemKey(item)}
            >
              <button
                className={itemKey(item) === selectedKey ? 'list-group-item-action suggestion-select selected' : 'list-group-item-action suggestion-select'}
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
          <button className="btn btn-sm btn-outline-secondary" aria-label="Iepriekšējā lapa" title="Iepriekšējā lapa"
            disabled={page === 0} onClick={() => onPageChange(page - 1)} type="button">
            ‹
          </button>
          <span>{page + 1} / {totalPages}</span>
          <button className="btn btn-sm btn-outline-secondary"
            aria-label="Nākamā lapa" title="Nākamā lapa"
            disabled={page + 1 >= totalPages}
            onClick={() => onPageChange(page + 1)}
            type="button"
          >
            ›
          </button>
        </div>
      )}
    </aside>
  )
}
