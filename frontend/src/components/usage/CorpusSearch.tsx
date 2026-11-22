import { useEffect, useRef, useState, type FormEvent } from 'react'
import { getCorpora, searchCorpus } from '../../api/client'
import type { CorpusOption, CorpusSearchResult, SaveExample, SelectCorpusExamples, Example } from '../../api/types'
import { CorpusExamplesDialog } from './CorpusExamplesDialog'
import { CorpusSelector } from './CorpusSelector'

type Props = {
  term: string
  examples: Example[]
  saving: boolean
  editable: boolean
  onSave: (example: SaveExample) => Promise<void>
  onSelectCorpus: (selection: SelectCorpusExamples) => Promise<void>
  onRemove: (id: string) => Promise<void>
  onRemoveCorpus: (corpus: string) => Promise<void>
  onAddSource: () => void
}

type CorpusSearchState = {
  id: string
  label: string
  result: CorpusSearchResult | null
  loading: boolean
  error: string
  requestId: number
  requestedPage: number
}

function createCorpusSearchState(corpus: CorpusOption): CorpusSearchState {
  return { id: corpus.id, label: corpus.label, result: null, loading: true,
    error: '', requestId: 0, requestedPage: 1 }
}

async function loadCorpusPage(query: string, corpusSearch: CorpusSearchState, page: number, exactOnly: boolean) {
  try {
    const result = await searchCorpus(query, corpusSearch.id, page, exactOnly)
    return { ...corpusSearch, loading: false, result, error: '', requestedPage: page }
  } catch (cause) {
    const error = cause instanceof Error ? cause.message : 'Neizdevās meklēt korpusā.'
    return { ...corpusSearch, loading: false, error, requestedPage: page }
  }
}

async function searchCorpora(
  query: string,
  exactOnly: boolean,
  corpusSearches: CorpusSearchState[],
  shouldContinueSearching: () => boolean,
  onResult: (corpusSearch: CorpusSearchState) => void,
) {
  let nextCorpusIndex = 0

  async function searchNextCorpus() {
    while (shouldContinueSearching() && nextCorpusIndex < corpusSearches.length) {
      const corpusSearch = corpusSearches[nextCorpusIndex]
      nextCorpusIndex += 1
      const result = await loadCorpusPage(query, corpusSearch, 1, exactOnly)
      if (!shouldContinueSearching()) {
        return
      }
      onResult(result)
    }
  }

  await Promise.all([searchNextCorpus(), searchNextCorpus(), searchNextCorpus()])
}

function compareMatchCounts(first: CorpusSearchState, second: CorpusSearchState) {
  const firstCount = first.result?.total ?? 0
  const secondCount = second.result?.total ?? 0
  return secondCount - firstCount
}

export function CorpusSearch({ term, examples, saving, editable, onSave, onSelectCorpus, onRemove, onRemoveCorpus, onAddSource }: Props) {
  const [corpusSearches, setCorpusSearches] = useState<CorpusSearchState[]>([])
  const [searching, setSearching] = useState(true)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const [searchInput, setSearchInput] = useState(term)
  const [query, setQuery] = useState(term)
  const [exactOnly, setExactOnly] = useState(false)
  const [openedCorpusId, setOpenedCorpusId] = useState<string | null>(null)
  const nextPageRequestId = useRef(0)
  const openedCorpusSearch = corpusSearches.find((corpusSearch) => corpusSearch.id === openedCorpusId)
  const openedCorpusResult = openedCorpusSearch?.result
  const openedCorpusExamples = examples.filter((example) => example.corpus === openedCorpusId)

  useEffect(() => {
    let searchIsOutdated = false
    async function searchAllCorpora() {
      try {
        const corpora = await getCorpora()
        if (searchIsOutdated) {
          return
        }
        const pendingCorpora = corpora.map(createCorpusSearchState)
        setCorpusSearches(pendingCorpora)
        await searchCorpora(query, exactOnly, pendingCorpora, () => !searchIsOutdated, updateCorpusSearch)
      } catch (cause) {
        if (!searchIsOutdated) {
          setError(cause instanceof Error ? cause.message : 'Neizdevās ielādēt korpusu sarakstu')
        }
      } finally {
        if (!searchIsOutdated) {
          setSearching(false)
        }
      }
    }
    searchAllCorpora()
    return () => {
      searchIsOutdated = true
    }
  }, [query, exactOnly, retry])

  function updateCorpusSearch(updatedCorpusSearch: CorpusSearchState) {
    setCorpusSearches((current) => current.map((corpusSearch) => corpusSearch.id === updatedCorpusSearch.id ? updatedCorpusSearch : corpusSearch))
  }

  async function loadCorpusPageNumber(corpusSearch: CorpusSearchState, page: number) {
    nextPageRequestId.current += 1
    const requestId = nextPageRequestId.current
    const loadingCorpusSearch = { ...corpusSearch, loading: true, error: '', requestId }
    updateCorpusSearch(loadingCorpusSearch)
    const updatedCorpusSearch = await loadCorpusPage(query, loadingCorpusSearch, page, exactOnly)
    setCorpusSearches((current) => current.map((item) => {
      const isCurrentRequest = item.id === corpusSearch.id && item.requestId === requestId
      return isCurrentRequest ? updatedCorpusSearch : item
    }))
  }

  function startSearch(nextQuery: string) {
    if (saving) return
    setCorpusSearches([])
    setError('')
    setSearching(true)
    setOpenedCorpusId(null)
    setQuery(nextQuery)
    setRetry((value) => value + 1)
  }

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextQuery = searchInput.trim()
    if (!nextQuery) {
      return
    }
    startSearch(nextQuery)
  }

  const sortedCorpora = [...corpusSearches].sort(compareMatchCounts)
  const searchFailed = corpusSearches.some((corpusSearch) => corpusSearch.error)
  let emptyResultsMessage = 'Korpusos sakritību nav.'
  if (searching) {
    emptyResultsMessage = 'Meklēšana turpinās...'
  } else if (searchFailed) {
    emptyResultsMessage = 'Pieejamajos korpusos sakritību nav, bet daļa korpusu nebija pieejami.'
  }

  return (
    <div className="usage-search">
      <form className="corpus-controls" onSubmit={submitSearch}>
        <p className="muted">
          Meklējiet lietojumu{corpusSearches.length > 0 ? ` ${corpusSearches.length} korpusos` : ' korpusos'}.
        </p>
        <div className="corpus-search-field">
          <input className="form-control form-control-sm" aria-label="Meklēt korpusos" value={searchInput} required maxLength={255} disabled={saving}
            onChange={(event) => setSearchInput(event.target.value)} />
          <button className="icon-button" type="submit" aria-label="Meklēt korpusos" disabled={saving}>
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden="true">
              <circle cx="10" cy="10" r="6" />
              <path d="m15 15 6 6" />
            </svg>
          </button>
        </div>
        <label className="corpus-exact-match">
          <input type="checkbox" checked={exactOnly} disabled={saving}
            onChange={(event) => {
              setExactOnly(event.target.checked)
              startSearch(searchInput.trim() || query)
            }} />
          Tikai precīzas sakritības
        </label>
      </form>
      {searching && sortedCorpora.length > 0 && <p className="muted">Meklēšana turpinās...</p>}
      {!searching && searchFailed && sortedCorpora.length > 0 && (
        <p className="muted">Daļa korpusu nebija pieejami.</p>
      )}
      {error && <p className="error" role="alert">{error}</p>}
      {(error || searchFailed) && <button className="btn btn-sm btn-outline-secondary" type="button" disabled={searching || saving}
        onClick={() => startSearch(query)}>Atkārtot meklēšanu</button>}
      {sortedCorpora.length > 0 ? <CorpusSelector
        choices={sortedCorpora.map((corpus) => ({ id: corpus.id, label: corpus.label,
          total: corpus.result?.total ?? null, error: corpus.error }))}
        disabled={saving} onSelect={setOpenedCorpusId} />
        : !error && <p className="muted">{emptyResultsMessage}</p>}
      {openedCorpusSearch && <CorpusExamplesDialog key={openedCorpusId}
        corpusId={openedCorpusSearch.id} label={openedCorpusSearch.label} result={openedCorpusResult ?? null}
        loading={openedCorpusSearch.loading} error={openedCorpusSearch.error} examples={openedCorpusExamples}
        saving={saving} editable={editable} exactOnly={exactOnly} onSave={onSave} onSelectCorpus={onSelectCorpus}
        onRemove={onRemove} onRemoveCorpus={onRemoveCorpus}
        onPageChange={(page) => loadCorpusPageNumber(openedCorpusSearch, page)} onClose={() => setOpenedCorpusId(null)} />}
      <div className="corpus-footer">
        <button type="button" className="add-source-button" disabled={saving || !editable} onClick={onAddSource}>
          Pievienot citu avotu
        </button>
      </div>
    </div>
  )
}
