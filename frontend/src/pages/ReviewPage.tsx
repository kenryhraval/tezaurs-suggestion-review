import { useEffect, useState } from 'react'
import { changeSuggestionStatus, getSuggestions, linkDuplicateSuggestion } from '../api'
import { SuggestionDetails } from '../components/SuggestionDetails'
import { SuggestionList } from '../components/SuggestionList'
import type { Suggestion, SuggestionSort, SuggestionStatus } from '../types'

const PAGE_SIZE = 20

export function ReviewPage() {
  const [suggestions, setSuggestions] = useState<Suggestion[]>([])
  const [status, setStatus] = useState<SuggestionStatus>('NEW')
  const [sort, setSort] = useState<SuggestionSort>('NEWEST')
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalItems, setTotalItems] = useState(0)
  const [selected, setSelected] = useState<Suggestion | null>(null)
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

    getSuggestions(status, query, page, PAGE_SIZE, sort)
      .then((result) => {
        if (!active) return
        if (result.totalPages > 0 && page >= result.totalPages) {
          setPage(result.totalPages - 1)
          return
        }
        setSuggestions(result.items)
        setTotalPages(result.totalPages)
        setTotalItems(result.totalItems)
        setSelected((current) => {
          if (current && current.status !== status) return current
          return result.items.find((item) => item.id === current?.id)
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

  function updateSuggestion(updated: Suggestion) {
    if (updated.status !== status) {
      removeSuggestion(updated.id, updated)
      return
    }
    if (!suggestions.some((item) => item.id === updated.id)) {
      setSelected(updated)
      setReload((value) => value + 1)
      return
    }
    setSuggestions((items) =>
      items.map((item) => (item.id === updated.id ? updated : item)),
    )
    setSelected(updated)
  }

  function removeSuggestion(id: number, keepOpen?: Suggestion) {
    const index = suggestions.findIndex((suggestion) => suggestion.id === id)
    const next = suggestions[index + 1] ?? suggestions[index - 1] ?? null

    setSuggestions((items) => items.filter((item) => item.id !== id))
    setTotalItems((count) => Math.max(0, count - 1))
    setSelected(keepOpen ?? next)
    setReload((value) => value + 1)
  }

  async function changeStatus(
    id: number,
    nextStatus: SuggestionStatus,
    keepOpen = false,
  ) {
    setError('')
    try {
      const updated = await changeSuggestionStatus(id, nextStatus)
      if (updated.status === status) updateSuggestion(updated)
      else removeSuggestion(id, keepOpen ? updated : undefined)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās mainīt statusu.')
    }
  }

  async function linkDuplicate(id: number, reviewId: string) {
    setError('')
    try {
      await linkDuplicateSuggestion(id, reviewId)
      removeSuggestion(id)
    } catch (cause) {
      const message = cause instanceof Error ? cause.message : 'Neizdevās piesaistīt dublikātu.'
      setError(message)
      throw cause
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
          suggestions={suggestions}
          status={status}
          sort={sort}
          search={search}
          selectedId={selected?.id ?? null}
          loading={loading}
          page={page}
          totalPages={totalPages}
          totalItems={totalItems}
          onSelect={(id) => setSelected(
            suggestions.find((suggestion) => suggestion.id === id) ?? null,
          )}
          onFilterStatusChange={changeFilterStatus}
          onSortChange={changeSort}
          onSearchChange={setSearch}
          onPageChange={changePage}
          onSuggestionStatusChange={changeStatus}
        />

        {!selected && !loading && (
          <section className="details">
            <p>Izvēlieties ieteikumu.</p>
          </section>
        )}

        {selected && (
          <SuggestionDetails
            key={selected.id}
            suggestion={selected}
            onStatusChange={(nextStatus) => changeStatus(selected.id, nextStatus, true)}
            onDuplicate={linkDuplicate}
          />
        )}
      </div>
    </main>
  )
}
