import { reviewStatusLabels } from '../../review/statuses'
import type { ReviewListItem, ReviewSort, ReviewStatus } from '../../api/types'

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
  items: ReviewListItem[]
  status: ReviewStatus
  sort: ReviewSort
  selectedKey: string | null
  loading: boolean
  page: number
  totalPages: number
  totalItems: number
  onSelect: (key: string) => void
  onFilterStatusChange: (status: ReviewStatus) => void
  onSortChange: (sort: ReviewSort) => void
  onPageChange: (page: number) => void
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
          onChange={(event) => onSortChange(event.target.value as ReviewSort)}
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
              key={item.id}
            >
              <button
                className={item.id === selectedKey ? 'list-group-item-action suggestion-select selected' : 'list-group-item-action suggestion-select'}
                onClick={() => onSelect(item.id)}
                type="button"
              >
                <strong>{item.reviewedTerm}</strong>
                {item.status !== 'NEW' && (
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
