import { Fragment } from 'react'

type Props = {
  page: number
  totalPages: number
  disabled?: boolean
  label?: string
  onPageChange: (page: number) => void
}

export function Pagination({ page, totalPages, disabled = false, label = 'Lapošana', onPageChange }: Props) {
  if (totalPages <= 1) {
    return null
  }

  const visiblePages: number[] = []
  if (totalPages <= 7) {
    for (let pageNumber = 0; pageNumber < totalPages; pageNumber++) {
      visiblePages.push(pageNumber)
    }
  } else {
    visiblePages.push(0)
    // Keep three nearby pages between the first and last page.
    const firstNearbyPage = Math.max(1, Math.min(page - 1, totalPages - 4))
    for (let pageNumber = firstNearbyPage; pageNumber <= firstNearbyPage + 2; pageNumber++) {
      visiblePages.push(pageNumber)
    }
    visiblePages.push(totalPages - 1)
  }

  return (
    <nav className="page-navigation" aria-label={label}>
      <ul className="pagination pagination-sm">
        <li className={disabled || page === 0 ? 'page-item disabled' : 'page-item'}>
          <button className="page-link" type="button" title="Iepriekšējā lapa" aria-label="Iepriekšējā lapa"
            disabled={disabled || page === 0} onClick={() => onPageChange(page - 1)}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
              strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="m14 6-6 6 6 6" />
            </svg>
          </button>
        </li>
        {visiblePages.map((pageNumber, index) => (
          <Fragment key={pageNumber}>
            {index > 0 && pageNumber > visiblePages[index - 1] + 1 && (
              <li className="page-item disabled" aria-hidden="true"><span className="page-link">…</span></li>
            )}
            <li className={pageNumber === page ? 'page-item active' : 'page-item'}>
              <button className="page-link" type="button" aria-label={`${pageNumber + 1}. lapa`}
                aria-current={pageNumber === page ? 'page' : undefined}
                disabled={disabled || pageNumber === page} onClick={() => onPageChange(pageNumber)}>
                {pageNumber + 1}
              </button>
            </li>
          </Fragment>
        ))}
        <li className={disabled || page + 1 >= totalPages ? 'page-item disabled' : 'page-item'}>
          <button className="page-link" type="button" title="Nākamā lapa" aria-label="Nākamā lapa"
            disabled={disabled || page + 1 >= totalPages} onClick={() => onPageChange(page + 1)}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
              strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="m10 6 6 6-6 6" />
            </svg>
          </button>
        </li>
      </ul>
    </nav>
  )
}
