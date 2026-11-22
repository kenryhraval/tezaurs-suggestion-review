import { reviewTypeLabels, reviewStatusLabels } from '../../review/statuses'
import type { ReviewType, ReviewListItem, ReviewSort, ReviewStatus } from '../../api/types'
import { casePath } from '../../review/links'
import { Link } from 'react-router'
import { Pagination } from '../shared/Pagination'

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
const reviewTypes: ReviewType[] = ['ENTRY', 'SENSE']

type Props = {
  items: ReviewListItem[]
  status: ReviewStatus | null
  types: ReviewType[]
  sort: ReviewSort
  selectedKey: string | null
  loading: boolean
  page: number
  totalPages: number
  totalItems: number
  onFilterStatusChange: (status: ReviewStatus | null) => void
  onTypesChange: (types: ReviewType[]) => void
  onSortChange: (sort: ReviewSort) => void
  onPageChange: (page: number) => void
}

export function SuggestionList({
  items,
  status,
  types,
  sort,
  selectedKey,
  loading,
  page,
  totalPages,
  totalItems,
  onFilterStatusChange,
  onTypesChange,
  onSortChange,
  onPageChange,
}: Props) {
  function toggleType(type: ReviewType, checked: boolean) {
    if (checked) {
      onTypesChange([...types, type])
    } else {
      onTypesChange(types.filter((selectedType) => selectedType !== type))
    }
  }

  return (
    <aside aria-label="Ieteikumu saraksts">
      <div className="suggestion-filters">
        <select className="custom-select custom-select-sm" aria-label="Stāvoklis"
          onChange={(event) => onFilterStatusChange(event.target.value === '' ? null : event.target.value as ReviewStatus)}
          value={status ?? ''}
        >
          <option value="">Visi</option>
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
        <fieldset className="case-type-filters">
          <legend className="sr-only">Izskatīšanas tips (neatzīmējot — visi)</legend>
          {reviewTypes.map((type) => (
            <label className="checkbox-label" key={type}>
              <input type="checkbox" checked={types.includes(type)}
                onChange={(event) => toggleType(type, event.target.checked)} />
              {reviewTypeLabels[type]}
            </label>
          ))}
        </fieldset>
      </div>

      <p className="suggestion-count" role="status">
        {loading ? 'Ielādē...' : `${totalItems} atbilstoši ieraksti`}
      </p>

      <section className="suggestion-group" aria-label={status === null ? 'Visi' : reviewStatusLabels[status]} aria-busy={loading}>
        {!loading && items.length === 0 && <p className="empty-group">Nav ieteikumu.</p>}

        {items.length > 0 && (
          <ul className="suggestion-list" inert={loading}>
            {items.map((item) => (
              <li key={item.id}>
                <Link
                  className={item.id === selectedKey ? 'suggestion-select selected' : 'suggestion-select'}
                  aria-current={item.id === selectedKey ? 'page' : undefined}
                  to={casePath(item.publicId)}
                >
                  <span>{item.reviewedTerm}</span>
                  {item.status !== 'NEW' && (
                    <small>
                      {item.sourceCount}{' '}
                      {item.sourceCount === 1 ? 'ieteikums' : 'ieteikumi'}
                    </small>
                  )}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <Pagination page={page} totalPages={totalPages} disabled={loading}
        label="Ieteikumu saraksta lapošana" onPageChange={onPageChange} />
    </aside>
  )
}
