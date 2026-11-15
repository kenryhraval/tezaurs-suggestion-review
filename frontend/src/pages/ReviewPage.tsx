import { useEffect, useState } from 'react'
import {
  changeReviewStatus,
  getReviews,
  getCurrentUser,
  mergeReviewCases,
} from '../api/client'
import { ReviewDetails } from '../components/review/ReviewDetails'
import { SuggestionList } from '../components/review/SuggestionList'
import { AppHeader } from '../components/layout/AppHeader'
import type { ReviewListItem, Review, ReviewSort, ReviewStatus } from '../api/types'

const PAGE_SIZE = 20

function itemFromReview(review: Review): ReviewListItem {
  return {
    id: review.id,
    reviewedTerm: review.reviewedTerm,
    status: review.status,
    createdAt: review.createdAt,
    sourceCount: review.sources.length,
  }
}

export function ReviewPage() {
  const [items, setItems] = useState<ReviewListItem[]>([])
  const [status, setStatus] = useState<ReviewStatus>('NEW')
  const [sort, setSort] = useState<ReviewSort>('NEWEST')
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalItems, setTotalItems] = useState(0)
  const [selected, setSelected] = useState<ReviewListItem | null>(null)
  const [loadedReview, setLoadedReview] = useState<Review | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reloadCount, setReloadCount] = useState(0)
  const [username, setUsername] = useState<string | null>(null)

  useEffect(() => {
    let requestIsOutdated = false
    async function loadCurrentUser() {
      try {
        const user = await getCurrentUser()
        if (!requestIsOutdated) {
          setUsername(user.username)
        }
      } catch {
        if (!requestIsOutdated) {
          setUsername(null)
        }
      }
    }
    loadCurrentUser()
    return () => { requestIsOutdated = true }
  }, [])

  useEffect(() => {
    const timeout = window.setTimeout(() => {
      const nextQuery = search.trim()
      if (nextQuery === query) {
        return
      }
      setLoading(true)
      setSelected(null)
      setLoadedReview(null)
      setPage(0)
      setQuery(nextQuery)
    }, 300)
    return () => window.clearTimeout(timeout)
  }, [search, query])

  useEffect(() => {
    let requestIsOutdated = false

    async function loadQueue() {
      try {
        const result = await getReviews(status, query, page, PAGE_SIZE, sort)
        if (requestIsOutdated) {
          return
        }

        if (result.totalPages > 0 && page >= result.totalPages) {
          setPage(result.totalPages - 1)
          return
        }
        setItems(result.items)
        setTotalPages(result.totalPages)
        setTotalItems(result.totalItems)
        setSelected((current) => {
          const selectedReviewMovedToAnotherStatus = current && current.status !== status
          if (selectedReviewMovedToAnotherStatus) {
            return current
          }
          const previousSelection = result.items.find((item) => current && item.id === current.id)
          return previousSelection ?? result.items[0] ?? null
        })
        setError('')
      } catch (cause) {
        if (!requestIsOutdated) {
          setError(cause instanceof Error ? cause.message : 'Neizdevās ielādēt ieteikumus.')
        }
      } finally {
        if (!requestIsOutdated) {
          setLoading(false)
        }
      }
    }

    loadQueue()

    return () => {
      requestIsOutdated = true
    }
  }, [status, query, page, sort, reloadCount])

  function changeFilterStatus(nextStatus: ReviewStatus) {
    setLoading(true)
    setSelected(null)
    setLoadedReview(null)
    setStatus(nextStatus)
    setPage(0)
    if (nextStatus === 'NEW' && sort === 'MOST_SUGGESTIONS') {
      setSort('NEWEST')
    }
  }

  function changeSort(nextSort: ReviewSort) {
    setLoading(true)
    setSelected(null)
    setLoadedReview(null)
    setSort(nextSort)
    setPage(0)
  }

  function changePage(nextPage: number) {
    setLoading(true)
    setSelected(null)
    setLoadedReview(null)
    setPage(nextPage)
  }

  function removeItem(item: ReviewListItem, keepOpen?: ReviewListItem) {
    const key = item.id
    const index = items.findIndex((candidate) => candidate.id === key)
    const next = items[index + 1] ?? items[index - 1] ?? null

    if (index >= 0) {
      setItems((currentItems) => currentItems.filter((candidate) => candidate.id !== key))
      setTotalItems((count) => Math.max(0, count - 1))
    }
    setSelected(keepOpen ?? next)
    setReloadCount((value) => value + 1)
  }

  async function changeStatus(nextStatus: ReviewStatus): Promise<Review> {
    if (!selected) {
      throw new Error('Izvēlieties izskatīšanu.')
    }
    const review = await changeReviewStatus(selected.id, nextStatus)
    handleReviewUpdated(review)
    return review
  }

  async function linkDuplicate(sourceCaseId: string, targetCaseId: string) {
    const target = await mergeReviewCases(sourceCaseId, targetCaseId)
    setLoadedReview(target)
    if (selected?.id === sourceCaseId) {
      removeItem(selected, itemFromReview(target))
    }
  }

  function handleReviewUpdated(review: Review) {
    setLoadedReview(review)
    if (selected && review.status !== selected.status) {
      removeItem(selected, itemFromReview(review))
    } else {
      const updated = itemFromReview(review)
      setItems((current) => current.map((item) => item.id === updated.id ? updated : item))
      setSelected(updated)
      setReloadCount((value) => value + 1)
    }
  }

  function selectItem(key: string) {
    const selectedItem = items.find((item) => item.id === key)
    setSelected(selectedItem ?? null)
    setLoadedReview(null)
  }

  function submitSearch() {
    const nextQuery = search.trim()
    if (nextQuery === query) return
    setLoading(true)
    setSelected(null)
    setLoadedReview(null)
    setPage(0)
    setQuery(nextQuery)
  }

  return (
    <main className="app-shell">
      <AppHeader reloadCount={reloadCount} search={search}
        onSearchChange={setSearch} onSearchSubmit={submitSearch} />

      {error && <p className="error">{error}</p>}

      <div className="layout">
        <SuggestionList
          items={items}
          status={status}
          sort={sort}
          selectedKey={selected?.id ?? null}
          loading={loading}
          page={page}
          totalPages={totalPages}
          totalItems={totalItems}
          onSelect={selectItem}
          onFilterStatusChange={changeFilterStatus}
          onSortChange={changeSort}
          onPageChange={changePage}
        />

        {!selected && !loading && (
          <section className="details">
            <p>Izvēlieties ieteikumu.</p>
          </section>
        )}

        {selected && (
          <ReviewDetails
            key={selected.id}
            item={selected}
            initialReview={loadedReview?.id === selected.id ? loadedReview : null}
            username={username}
            onStatusChange={changeStatus}
            onReviewUpdated={handleReviewUpdated}
            onDuplicate={linkDuplicate}
          />
        )}
      </div>
    </main>
  )
}
