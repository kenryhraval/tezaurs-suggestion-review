import { useCallback, useEffect, useState } from 'react'
import { Navigate, Outlet, useLocation, useRouteLoaderData, useNavigation } from 'react-router'
import { getReviews } from '../api/client'
import type { ReviewPageContext } from './ReviewPage'
import { ReviewLoading } from '../components/review/ReviewLoading'
import { SuggestionList } from '../components/review/SuggestionList'
import { AppHeader } from '../components/layout/AppHeader'
import type { ReviewType, Review, ReviewPageResponse, ReviewSort, ReviewStatus } from '../api/types'
import { reviewPath } from '../review/links'

const PAGE_SIZE = 20

type QueueFilters = {
  status: ReviewStatus | null
  types: ReviewType[]
  sort: ReviewSort
  query: string
  page: number
}

type QueueResult = {
  data: ReviewPageResponse
  filters: QueueFilters | null
  error: string
}

const EMPTY_QUEUE: ReviewPageResponse = { items: [], page: 0, totalPages: 0, totalItems: 0 }
const DEFAULT_FILTERS: QueueFilters = { status: null, types: [], sort: 'NEWEST', query: '', page: 0 }

export function MainPage() {
  const selectedReview = useRouteLoaderData<Review>('review')
  const location = useLocation()
  const navigation = useNavigation()
  const [queueFilters, setQueueFilters] = useState<QueueFilters>(DEFAULT_FILTERS)
  const [queue, setQueue] = useState<QueueResult>({ data: EMPTY_QUEUE, filters: null, error: '' })
  const [reloadCount, setReloadCount] = useState(0)

  const { status, types, sort, query, page } = queueFilters
  // Each response keeps the filter state that started its request.
  const loading = queue.filters !== queueFilters
  const { items, totalPages, totalItems } = queue.data

  useEffect(() => {
    let requestIsOutdated = false
    const { status, types, sort, query, page } = queueFilters
    async function loadQueue() {
      try {
        const result = await getReviews(status, query, page, PAGE_SIZE, sort, types)
        if (requestIsOutdated) return
        if (result.totalPages > 0 && page >= result.totalPages) {
          setQueueFilters({ ...queueFilters, page: result.totalPages - 1 })
          return
        }
        setQueue({ data: result, filters: queueFilters, error: '' })
      } catch (cause) {
        if (!requestIsOutdated) {
          setQueue({
            data: EMPTY_QUEUE,
            filters: queueFilters,
            error: cause instanceof Error ? cause.message : 'Neizdevās ielādēt ieteikumus.',
          })
        }
      }
    }
    loadQueue()
    return () => { requestIsOutdated = true }
  }, [queueFilters, reloadCount])

  function changeFilterStatus(status: ReviewStatus | null) {
    setQueueFilters((current) => {
      let sort = current.sort
      if (status === 'NEW' && sort === 'MOST_SUGGESTIONS') {
        sort = 'NEWEST'
      }
      return { ...current, status, sort, page: 0 }
    })
  }

  function changeSort(sort: ReviewSort) {
    setQueueFilters((current) => ({ ...current, sort, page: 0 }))
  }

  function changeTypes(types: ReviewType[]) {
    setQueueFilters((current) => ({ ...current, types, page: 0 }))
  }

  function changePage(page: number) {
    setQueueFilters((current) => ({ ...current, page }))
  }

  const searchQueue = useCallback((searchText: string) => {
    setQueueFilters((current) => ({ ...current, query: searchText, page: 0 }))
  }, [])

  function resetQueue() {
    setQueueFilters(DEFAULT_FILTERS)
  }

  function reloadQueue() {
    setReloadCount((current) => current + 1)
  }

  function renderReviewContent() {
    if (navigation.state === 'loading') {
      return <ReviewLoading />
    }

    if (location.pathname !== '/') {
      const context: ReviewPageContext = {
        onReviewUpdated: reloadQueue,
      }
      return <Outlet context={context} />
    }

    if (loading || queue.error) {
      return null
    }

    const firstItem = items[0]
    if (firstItem) {
      return <Navigate to={reviewPath(firstItem.publicId)} replace />
    }

    return <section className="details"><p>Atlasē nav ieteikumu.</p></section>
  }

  return (
    <main className="app-shell">
      <AppHeader reloadCount={reloadCount} query={query} onSearch={searchQueue} onHome={resetQueue} />
      {queue.error && <p className="error">{queue.error}</p>}
      <div className="layout">
        <SuggestionList items={items} status={status} types={types} sort={sort}
          selectedKey={selectedReview?.id ?? null} loading={loading}
          page={page} totalPages={totalPages} totalItems={totalItems}
          onFilterStatusChange={changeFilterStatus} onTypesChange={changeTypes}
          onSortChange={changeSort} onPageChange={changePage} />

        {renderReviewContent()}
      </div>
    </main>
  )
}
