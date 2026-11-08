import { useEffect, useRef, useState, type FormEvent } from 'react'
import { getCorpora, searchCorpus } from '../../api/client'
import type { CorpusOption, CorpusExample, CorpusSearchResult, SaveUsageEvidence, UsageEvidence } from '../../api/types'
import { SavedUsageEvidence } from './SavedUsageEvidence'
import { UsageSourceLink } from './UsageSourceLink'

type Props = {
  reviewId: string
  term: string
  evidence: UsageEvidence[]
  saving: boolean
  onSave: (evidence: SaveUsageEvidence) => Promise<void>
  onRemove: (id: string) => void
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

async function loadCorpusPage(reviewId: string, query: string, corpusSearch: CorpusSearchState, page: number) {
  try {
    const result = await searchCorpus(reviewId, query, corpusSearch.id, page)
    return { ...corpusSearch, loading: false, result, error: '', requestedPage: page }
  } catch (cause) {
    const error = cause instanceof Error ? cause.message : 'Neizdevās meklēt korpusā.'
    return { ...corpusSearch, loading: false, error, requestedPage: page }
  }
}

async function searchCorpora(
  reviewId: string,
  query: string,
  corpusSearches: CorpusSearchState[],
  shouldContinueSearching: () => boolean,
  onResult: (corpusSearch: CorpusSearchState) => void,
) {
  const completedCorpora: CorpusSearchState[] = []
  let nextCorpusIndex = 0

  async function searchNextCorpus() {
    while (shouldContinueSearching() && nextCorpusIndex < corpusSearches.length) {
      const corpusSearch = corpusSearches[nextCorpusIndex]
      nextCorpusIndex += 1
      const result = await loadCorpusPage(reviewId, query, corpusSearch, 1)
      if (!shouldContinueSearching()) {
        return
      }
      completedCorpora.push(result)
      onResult(result)
    }
  }

  await Promise.all([searchNextCorpus(), searchNextCorpus(), searchNextCorpus()])
  return completedCorpora
}

function compareMatchCounts(first: CorpusSearchState, second: CorpusSearchState) {
  const firstCount = first.result?.total ?? 0
  const secondCount = second.result?.total ?? 0
  return secondCount - firstCount
}

export function CorpusSearch({ reviewId, term, evidence, saving, onSave, onRemove, onAddSource }: Props) {
  const [corpusSearches, setCorpusSearches] = useState<CorpusSearchState[]>([])
  const [searching, setSearching] = useState(true)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const [searchInput, setSearchInput] = useState(term)
  const [query, setQuery] = useState(term)
  const [selectedCorpusId, setSelectedCorpusId] = useState<string | null>(null)
  const nextPageRequestId = useRef(0)

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
        const completedCorpora = await searchCorpora(reviewId, query, pendingCorpora, () => !searchIsOutdated, updateCorpusSearch)
        if (searchIsOutdated) {
          return
        }
        const corporaWithMatches = completedCorpora.filter((corpus) => corpus.result && corpus.result.total > 0)
        corporaWithMatches.sort(compareMatchCounts)
        const corpusWithMostMatches = corporaWithMatches[0]
        if (corpusWithMostMatches) {
          setSelectedCorpusId((current) => current ?? corpusWithMostMatches.id)
        }
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
  }, [reviewId, query, retry])

  function updateCorpusSearch(updatedCorpusSearch: CorpusSearchState) {
    setCorpusSearches((current) => current.map((corpusSearch) => corpusSearch.id === updatedCorpusSearch.id ? updatedCorpusSearch : corpusSearch))
  }

  async function loadCorpusPageNumber(corpusSearch: CorpusSearchState, page: number) {
    nextPageRequestId.current += 1
    const requestId = nextPageRequestId.current
    const loadingCorpusSearch = { ...corpusSearch, loading: true, error: '', requestId }
    updateCorpusSearch(loadingCorpusSearch)
    const updatedCorpusSearch = await loadCorpusPage(reviewId, query, loadingCorpusSearch, page)
    setCorpusSearches((current) => current.map((item) => {
      const isCurrentRequest = item.id === corpusSearch.id && item.requestId === requestId
      return isCurrentRequest ? updatedCorpusSearch : item
    }))
  }

  function startSearch(nextQuery: string) {
    setCorpusSearches([])
    setError('')
    setSearching(true)
    setSelectedCorpusId(null)
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

  async function saveCorpusExample(result: CorpusSearchResult, example: CorpusExample) {
    await onSave({ source: 'CORPUS', corpus: result.corpus, tokenNumber: example.tokenNumber,
      query: result.query, citation: example.citation, url: result.url,
      reference: example.reference || null, note: null })
  }

  const selectedCorpusSearch = corpusSearches.find((corpusSearch) => corpusSearch.id === selectedCorpusId)
  const selectedCorpusResult = selectedCorpusSearch?.result
  const corpusMatches = corpusSearches.filter((corpusSearch) => corpusSearch.result && corpusSearch.result.total > 0)
    .sort(compareMatchCounts)
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
          <input className="form-control form-control-sm" aria-label="Meklēt korpusos" value={searchInput} required maxLength={255}
            onChange={(event) => setSearchInput(event.target.value)} />
          <button className="icon-button" type="submit" aria-label="Meklēt korpusos">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden="true">
              <circle cx="10" cy="10" r="6" />
              <path d="m15 15 6 6" />
            </svg>
          </button>
        </div>
      </form>
      {searching && corpusMatches.length > 0 && <p className="muted">Meklēšana turpinās...</p>}
      {!searching && searchFailed && corpusMatches.length > 0 && (
        <p className="muted">Daļa korpusu nebija pieejami.</p>
      )}
      {error && <p className="error" role="alert">{error}</p>}
      {(error || searchFailed) && <button className="btn btn-sm btn-outline-secondary" type="button" disabled={searching}
        onClick={() => startSearch(query)}>Atkārtot meklēšanu</button>}
      {corpusMatches.length > 0 ? <ul className="corpus-badges" aria-label="Korpusu meklēšanas rezultāti">
        {corpusMatches.map((corpusSearch) => (
          <li key={corpusSearch.id}>
            <button type="button" aria-pressed={corpusSearch.id === selectedCorpusId}
              onClick={() => setSelectedCorpusId(corpusSearch.id)}>
              {corpusSearch.label}<span>{corpusSearch.result?.total}</span>
            </button>
          </li>
        ))}
      </ul> : !error && <p className="muted">{emptyResultsMessage}</p>}
      {selectedCorpusSearch && <div className="corpus-results">
        {selectedCorpusSearch.loading && <p className="muted">Gaida rezultātus...</p>}
        {selectedCorpusSearch.error && <p className="error" role="alert">{selectedCorpusSearch.error}{' '}
          <button className="btn btn-sm btn-outline-secondary" type="button" disabled={searching || saving} onClick={() => loadCorpusPageNumber(selectedCorpusSearch, selectedCorpusSearch.requestedPage)}>Atkārtot</button>
        </p>}
        {selectedCorpusResult && <>
          {selectedCorpusResult.examples.length === 0 && <p className="muted">Šajā meklēšanā piemēri netika atrasti.</p>}
          {selectedCorpusResult.examples.map((example) => {
            const saved = evidence.find((item) => item.corpus === selectedCorpusResult.corpus && item.tokenNumber === example.tokenNumber)
            const exampleText = <>{example.left}<strong>{example.match}</strong>{example.right}</>
            if (saved) {
              return <SavedUsageEvidence key={`${selectedCorpusResult.corpus}:${example.tokenNumber}`} evidence={saved} busy={saving || selectedCorpusSearch.loading} highlighted
              onRemove={() => onRemove(saved.id)}>{exampleText}</SavedUsageEvidence>
            }
            return <article className="usage-evidence" key={`${selectedCorpusResult.corpus}:${example.tokenNumber}`}>
              <div className="evidence-text">
                <p>{exampleText}</p>
                <button className="icon-button" title="Atzīmēt piemēru" aria-label="Atzīmēt piemēru"
                  disabled={saving || selectedCorpusSearch.loading} type="button" onClick={() => saveCorpusExample(selectedCorpusResult, example)}>
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" aria-hidden="true">
                    <path d="M6 3h12v18l-6-4-6 4Z" />
                  </svg>
                </button>
              </div>
              <UsageSourceLink label={example.sourceLabel} url={example.sourceUrl ?? selectedCorpusResult.url}
                originalSource={example.sourceUrl !== null} />
            </article>
          })}
        </>}
      </div>}
      <div className="corpus-footer">
        <button type="button" className="btn btn-sm btn-outline-secondary add-source-button" disabled={saving} onClick={onAddSource}>
          <span aria-hidden="true">＋</span> Pievienot citu avotu
        </button>
        {selectedCorpusSearch && selectedCorpusResult && (selectedCorpusResult.page > 1 || selectedCorpusResult.page * selectedCorpusResult.pageSize < selectedCorpusResult.total) && <nav className="corpus-pagination" aria-label={`${selectedCorpusSearch.label}: lapošana`}>
          <button className="btn btn-sm btn-outline-secondary" type="button" title="Iepriekšējā lapa" aria-label="Iepriekšējā lapa"
            disabled={selectedCorpusSearch.loading || selectedCorpusResult.page === 1}
            onClick={() => loadCorpusPageNumber(selectedCorpusSearch, selectedCorpusResult.page - 1)}>‹</button>
          <small aria-live="polite">{selectedCorpusResult.page} / {Math.ceil(selectedCorpusResult.total / selectedCorpusResult.pageSize)}</small>
          <button className="btn btn-sm btn-outline-secondary" type="button" title="Nākamā lapa" aria-label="Nākamā lapa"
            disabled={selectedCorpusSearch.loading || selectedCorpusResult.page * selectedCorpusResult.pageSize >= selectedCorpusResult.total}
            onClick={() => loadCorpusPageNumber(selectedCorpusSearch, selectedCorpusResult.page + 1)}>›</button>
        </nav>}
      </div>
    </div>
  )
}
