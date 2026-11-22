import { useEffect, useRef, useState, type FormEvent } from 'react'
import { getCorpora, searchCorpus } from '../../api/client'
import type { CorpusOption, CorpusExample, CorpusSearchResult, SaveExample, SelectCorpusExamples, Example } from '../../api/types'
import { SavedExample } from './SavedExample'
import { UsageSourceLink } from './UsageSourceLink'
import { Pagination } from '../shared/Pagination'
import { CorpusSelector } from './CorpusSelector'

type Props = {
  term: string
  examples: Example[]
  saving: boolean
  onSave: (examples: SaveExample[]) => Promise<boolean>
  onSelectCorpus: (selection: SelectCorpusExamples) => Promise<void>
  onRemove: (id: string) => void
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
  const completedCorpora: CorpusSearchState[] = []
  let nextCorpusIndex = 0

  async function searchNextCorpus() {
    while (shouldContinueSearching() && nextCorpusIndex < corpusSearches.length) {
      const corpusSearch = corpusSearches[nextCorpusIndex]
      nextCorpusIndex += 1
      const result = await loadCorpusPage(query, corpusSearch, 1, exactOnly)
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

function createExampleRequest(result: CorpusSearchResult, example: CorpusExample): SaveExample {
  return { source: 'CORPUS', corpus: result.corpus, tokenNumber: example.tokenNumber,
    query: result.query, citation: example.citation, url: result.url,
    reference: example.reference || null, note: null }
}

export function CorpusSearch({ term, examples, saving, onSave, onSelectCorpus, onRemove, onRemoveCorpus, onAddSource }: Props) {
  const [corpusSearches, setCorpusSearches] = useState<CorpusSearchState[]>([])
  const [searching, setSearching] = useState(true)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const [searchInput, setSearchInput] = useState(term)
  const [query, setQuery] = useState(term)
  const [exactOnly, setExactOnly] = useState(false)
  const [selectedCorpusId, setSelectedCorpusId] = useState<string | null>(null)
  const [requestedSampleSize, setRequestedSampleSize] = useState(50)
  const nextPageRequestId = useRef(0)
  const resultsRef = useRef<HTMLDivElement>(null)
  const selectedCorpusSearch = corpusSearches.find((corpusSearch) => corpusSearch.id === selectedCorpusId)
  const selectedCorpusResult = selectedCorpusSearch?.result
  const sampleSize = Math.min(requestedSampleSize, selectedCorpusResult?.total ?? 0)
  const selectedCount = examples.filter((item) => item.corpus === selectedCorpusId).length
  const removableCount = examples.filter((item) => item.corpus === selectedCorpusId && !item.inDraft).length

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
        const completedCorpora = await searchCorpora(query, exactOnly, pendingCorpora, () => !searchIsOutdated, updateCorpusSearch)
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
  }, [query, exactOnly, retry])

  useEffect(() => {
    if (resultsRef.current) {
      resultsRef.current.scrollTop = 0
    }
  }, [selectedCorpusId, selectedCorpusResult?.page])

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
    await onSave([createExampleRequest(result, example)])
  }

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
      {searching && corpusMatches.length > 0 && <p className="muted">Meklēšana turpinās...</p>}
      {!searching && searchFailed && corpusMatches.length > 0 && (
        <p className="muted">Daļa korpusu nebija pieejami.</p>
      )}
      {error && <p className="error" role="alert">{error}</p>}
      {(error || searchFailed) && <button className="btn btn-sm btn-outline-secondary" type="button" disabled={searching || saving}
        onClick={() => startSearch(query)}>Atkārtot meklēšanu</button>}
      {corpusMatches.length > 0 ? <CorpusSelector
        choices={corpusMatches.map((corpus) => ({ id: corpus.id, label: corpus.label, total: corpus.result?.total ?? 0 }))}
        selectedId={selectedCorpusId} disabled={saving} onSelect={setSelectedCorpusId} />
        : !error && <p className="muted">{emptyResultsMessage}</p>}
      {selectedCorpusSearch && selectedCorpusResult && <div className="corpus-selection">
        <div className="corpus-selection-summary">
          <span>Atzīmēti šajā korpusā: <strong>{selectedCount}</strong></span>
        </div>
        {selectedCorpusResult.total > 0 && <div className="corpus-selection-actions">
          <div className="input-group input-group-sm corpus-sample-input">
            <input className="form-control" type="number" min={1} max={selectedCorpusResult.total}
              aria-label="Nejauši pievienojamo piemēru skaits" value={sampleSize} disabled={saving}
              onChange={(event) => setRequestedSampleSize(Number(event.target.value))} />
            <div className="input-group-append">
              <button className="btn btn-primary" type="button"
                disabled={saving || selectedCorpusSearch.loading || !!selectedCorpusSearch.error || !Number.isInteger(sampleSize) || sampleSize < 1}
                onClick={() => onSelectCorpus({ corpus: selectedCorpusResult.corpus, query, exactOnly, count: sampleSize })}>
                Pievienot nejaušus
              </button>
            </div>
          </div>
          <div className="d-flex">
            <button className="btn btn-sm btn-outline-secondary" type="button"
              disabled={saving || selectedCorpusSearch.loading || !!selectedCorpusSearch.error}
              onClick={() => onSelectCorpus({ corpus: selectedCorpusResult.corpus, query, exactOnly, count: null })}>
              Pievienot visus
            </button>
            <button className="btn btn-sm btn-outline-secondary ml-2" type="button" disabled={saving || removableCount === 0}
              title="Noņemt piemērus, kas nav piesaistīti melnraksta nozīmēm"
              onClick={() => onRemoveCorpus(selectedCorpusResult.corpus)}>Noņemt atlasi</button>
          </div>
          {saving && <small role="status">Saglabā...</small>}
        </div>}
      </div>}
      {selectedCorpusSearch && <div className="corpus-results" ref={resultsRef}
        role="region" aria-label={`${selectedCorpusSearch.label}: piemēri`} tabIndex={0}>
        {selectedCorpusSearch.loading && <p className="muted">Gaida rezultātus...</p>}
        {selectedCorpusSearch.error && <p className="error" role="alert">{selectedCorpusSearch.error}{' '}
          <button className="btn btn-sm btn-outline-secondary" type="button" disabled={searching || saving} onClick={() => loadCorpusPageNumber(selectedCorpusSearch, selectedCorpusSearch.requestedPage)}>Atkārtot</button>
        </p>}
        {selectedCorpusResult && <>
          {selectedCorpusResult.examples.length === 0 && <p className="muted">Šajā meklēšanā piemēri netika atrasti.</p>}
          {selectedCorpusResult.examples.map((example) => {
            const saved = examples.find((item) => item.corpus === selectedCorpusResult.corpus && item.tokenNumber === example.tokenNumber)
            const exampleText = <>{example.left}<strong>{example.match}</strong>{example.right}</>
            if (saved) {
              return <SavedExample key={`${selectedCorpusResult.corpus}:${example.tokenNumber}`} example={saved} busy={saving || selectedCorpusSearch.loading} highlighted
              onRemove={saved.inDraft ? undefined : () => onRemove(saved.id)}>{exampleText}</SavedExample>
            }
            return <article className="usage-example" key={`${selectedCorpusResult.corpus}:${example.tokenNumber}`}>
              <div className="example-text">
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
        {selectedCorpusSearch && selectedCorpusResult && <Pagination
          page={selectedCorpusResult.page - 1}
          totalPages={Math.ceil(selectedCorpusResult.total / selectedCorpusResult.pageSize)}
          disabled={selectedCorpusSearch.loading || saving} label={`${selectedCorpusSearch.label}: lapošana`}
          onPageChange={(page) => loadCorpusPageNumber(selectedCorpusSearch, page + 1)} />}
      </div>
    </div>
  )
}
