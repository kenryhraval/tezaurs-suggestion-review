import { useEffect, useState } from 'react'
import {
  changeReviewStatus,
  changeSuggestionStatus,
  getQueueItems,
  linkDuplicateSuggestion,
} from '../api'
import { SuggestionDetails } from '../components/SuggestionDetails'
import { SuggestionList } from '../components/SuggestionList'
import type { QueueItem, Review, SuggestionSort, SuggestionStatus } from '../types'

const PAGE_SIZE = 20

function itemKey(item: QueueItem) {
  return `${item.itemType}:${item.id}`
}

function itemFromReview(review: Review): QueueItem {
  return {
    itemType: 'REVIEW',
    id: review.id,
    reviewedTerm: review.reviewedTerm,
    status: review.status,
    createdAt: review.createdAt,
    sourceCount: review.sources.length,
  }
}

export function ReviewPage() {
  const [items, setItems] = useState<QueueItem[]>([])
  const [status, setStatus] = useState<SuggestionStatus>('NEW')
  const [sort, setSort] = useState<SuggestionSort>('NEWEST')
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalItems, setTotalItems] = useState(0)
  const [selected, setSelected] = useState<QueueItem | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)

  useEffect(() => {
    const timeout = window.setTimeout(() => {
      const nextQuery = search.trim()
      if (nextQuery === query) return
      setLoading(true)
      setSelected(null)
      setPage(0)
      setQuery(nextQuery)
    }, 300)
    return () => window.clearTimeout(timeout)
  }, [search, query])

  useEffect(() => {
    let active = true

    getQueueItems(status, query, page, PAGE_SIZE, sort)
      .then((result) => {
        if (!active) return
        if (result.totalPages > 0 && page >= result.totalPages) {
          setPage(result.totalPages - 1)
          return
        }
        setItems(result.items)
        setTotalPages(result.totalPages)
        setTotalItems(result.totalItems)
        setSelected((current) => {
          if (current && current.status !== status) return current
          return result.items.find((item) => current && itemKey(item) === itemKey(current))
            ?? result.items[0]
            ?? null
        })
        setError('')
      })
      .catch((cause) => {
        if (active) {
          setError(cause instanceof Error ? cause.message : 'Neizdevās ielādēt ieteikumus.')
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [status, query, page, sort, reload])

  function changeFilterStatus(nextStatus: SuggestionStatus) {
    setLoading(true)
    setSelected(null)
    setStatus(nextStatus)
    setPage(0)
    if (nextStatus === 'NEW' && sort === 'MOST_SUGGESTIONS') setSort('NEWEST')
  }

  function changeSort(nextSort: SuggestionSort) {
    setLoading(true)
    setSelected(null)
    setSort(nextSort)
    setPage(0)
  }

  function changePage(nextPage: number) {
    setLoading(true)
    setSelected(null)
    setPage(nextPage)
  }

  function removeItem(item: QueueItem, keepOpen?: QueueItem) {
    const key = itemKey(item)
    const index = items.findIndex((candidate) => itemKey(candidate) === key)
    const next = items[index + 1] ?? items[index - 1] ?? null

    if (index >= 0) {
      setItems((currentItems) => currentItems.filter((candidate) => itemKey(candidate) !== key))
      setTotalItems((count) => Math.max(0, count - 1))
    }
    setSelected(keepOpen ?? next)
    setReload((value) => value + 1)
  }

  async function changeStatus(
    item: QueueItem,
    nextStatus: SuggestionStatus,
    keepOpen = false,
  ): Promise<Review | null> {
    setError('')
    try {
      const review = item.itemType === 'SUGGESTION'
        ? await changeSuggestionStatus(item.id, nextStatus)
        : await changeReviewStatus(item.id, nextStatus)
      const updated = itemFromReview(review)
      removeItem(item, keepOpen ? updated : undefined)
      return review
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās mainīt statusu.')
      return null
    }
  }

  async function linkDuplicate(id: number, reviewId: string) {
    setError('')
    try {
      const target = await linkDuplicateSuggestion(id, reviewId)
      const source = selected?.itemType === 'REVIEW'
        ? selected : items.find((item) => item.itemType === 'SUGGESTION' && item.id === id)
      if (source) removeItem(source, itemFromReview(target))
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : 'Neizdevās piesaistīt dublikātu.'
      setError(message)
      throw cause
    }
  }

  function handleReviewUpdated(review: Review) {
    if (selected && review.status !== selected.status) {
      removeItem(selected, itemFromReview(review))
    } else {
      const updated = itemFromReview(review)
      setItems((current) => current.map((item) => itemKey(item) === itemKey(updated) ? updated : item))
      setSelected(updated)
      setReload((value) => value + 1)
    }
  }

  return (
    <main>
      <header>
        <h1>Tēzaura ieteikumu pārskatīšana</h1>
      </header>

      {error && <p className="error">{error}</p>}

      <div className="layout">
        <SuggestionList
          items={items}
          status={status}
          sort={sort}
          search={search}
          selectedKey={selected ? itemKey(selected) : null}
          loading={loading}
          page={page}
          totalPages={totalPages}
          totalItems={totalItems}
          onSelect={(key) => setSelected(
            items.find((item) => itemKey(item) === key) ?? null,
          )}
          onFilterStatusChange={changeFilterStatus}
          onSortChange={changeSort}
          onSearchChange={setSearch}
          onPageChange={changePage}
        />

        {!selected && !loading && (
          <section className="details">
            <p>Izvēlieties ieteikumu.</p>
          </section>
        )}

        {selected && (
          <SuggestionDetails
            key={itemKey(selected)}
            item={selected}
            onStatusChange={(nextStatus) => changeStatus(selected, nextStatus, true)}
            onReviewUpdated={handleReviewUpdated}
            onDuplicate={linkDuplicate}
          />
        )}
      </div>
    </main>
  )
}
