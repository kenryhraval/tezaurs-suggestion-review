import { useEffect, useRef, useState } from 'react'
import type { CorpusExample, CorpusSearchResult, Example, SaveExample, SelectCorpusExamples } from '../../api/types'
import { Dialog } from '../shared/Dialog'
import { Pagination } from '../shared/Pagination'
import { CorpusExampleRow } from './CorpusExampleRow'
import { ExampleText } from '../shared/ExampleText'

type Props = {
  corpusId: string
  label: string
  result: CorpusSearchResult | null
  loading: boolean
  error: string
  examples: Example[]
  saving: boolean
  editable: boolean
  exactOnly: boolean
  onSave: (example: SaveExample) => Promise<void>
  onSelectCorpus: (selection: SelectCorpusExamples) => Promise<void>
  onRemove: (id: string) => Promise<void>
  onRemoveCorpus: (corpus: string) => Promise<void>
  onPageChange: (page: number) => void
  onClose: () => void
}

const SELECTED_PAGE_SIZE = 25

export function CorpusExamplesDialog({ corpusId, label, result, loading, error, examples, saving, editable, exactOnly,
  onSave, onSelectCorpus, onRemove, onRemoveCorpus, onPageChange, onClose }: Props) {
  const [selectedOnly, setSelectedOnly] = useState(false)
  const [selectedPage, setSelectedPage] = useState(0)
  const [requestedSampleSize, setRequestedSampleSize] = useState(50)
  const [actionError, setActionError] = useState('')
  const listRef = useRef<HTMLDivElement>(null)
  const selectedPageCount = Math.ceil(examples.length / SELECTED_PAGE_SIZE)
  const currentSelectedPage = Math.min(selectedPage, Math.max(0, selectedPageCount - 1))
  const selectedOnPage = examples.slice(currentSelectedPage * SELECTED_PAGE_SIZE, (currentSelectedPage + 1) * SELECTED_PAGE_SIZE)
  const sampleSize = Math.min(requestedSampleSize, result?.total ?? 0)
  const canRemoveSelection = examples.some((example) => !example.approved)
  const busy = saving || (!selectedOnly && loading)

  useEffect(() => {
    if (listRef.current) listRef.current.scrollTop = 0
  }, [selectedOnly, currentSelectedPage, result?.page])

  async function perform(action: () => Promise<void>) {
    setActionError('')
    try {
      await action()
    } catch (cause) {
      setActionError(cause instanceof Error ? cause.message : 'Darbību neizdevās saglabāt.')
    }
  }

  async function selectExample(example: CorpusExample) {
    if (!result) return
    setActionError('')
    await onSave({ source: 'CORPUS', corpus: result.corpus, tokenNumber: example.tokenNumber,
      query: result.query, citation: example.citation,
      matchStart: example.left.trimStart().length, matchEnd: example.left.trimStart().length + example.match.length,
      url: result.url, reference: example.reference || null, note: null })
  }

  function renderSavedExample(example: Example) {
    return <CorpusExampleRow key={example.tokenNumber ?? example.id} selected busy={busy}
      sourceLabel={example.sourceLabel} sourceUrl={example.sourceUrl ?? example.url}
      originalSource={example.sourceUrl !== null}
      onToggle={!editable || example.approved ? undefined : () => perform(() => onRemove(example.id))}>
      <ExampleText example={example} />
    </CorpusExampleRow>
  }

  return <Dialog title={label} busy={saving} wide onCancel={onClose}>
    <div className="corpus-dialog-filter">
      {result && <span className="muted">Kopā {result.total} piemēri</span>}
      <label>
        <input type="checkbox" checked={selectedOnly} disabled={saving}
          onChange={(event) => { setSelectedOnly(event.target.checked); setSelectedPage(0) }} />
        Atzīmētie ({examples.length})
      </label>
    </div>
    <div className="corpus-selection-actions">
      {result && result.total > 0 && <div className="corpus-random-selection">
        <span>Nejauša atlase</span>
        <input className="form-control form-control-sm" type="number" min={1} max={result.total} value={sampleSize}
          aria-label="Nejauši pievienojamo piemēru skaits" disabled={busy || !editable}
          onChange={(event) => setRequestedSampleSize(Number(event.target.value))} />
        <button className="btn btn-sm btn-light border" type="button" disabled={busy || !editable || !Number.isInteger(sampleSize) || sampleSize < 1}
          onClick={() => perform(() => onSelectCorpus({ corpus: corpusId, query: result.query, exactOnly, count: sampleSize }))}>
          Pievienot
        </button>
      </div>}
      <div className="corpus-selection-shortcuts">
        {result && result.total > 0 && <button className="btn btn-sm btn-light border" type="button" disabled={busy || !editable}
          onClick={() => perform(() => onSelectCorpus({ corpus: corpusId, query: result.query, exactOnly, count: null }))}>
          Pievienot visus
        </button>}
        <button className="btn btn-sm btn-light border" type="button" disabled={busy || !editable || !canRemoveSelection}
          title="Noņemt neapstiprināto piemēru atlasi" onClick={() => perform(() => onRemoveCorpus(corpusId))}>Noņemt atlasi</button>
      </div>
      {saving && <small role="status">Saglabā...</small>}
    </div>
    {actionError && <p className="error" role="alert">{actionError}</p>}
    {!selectedOnly && error && <p className="error" role="alert">{error}{' '}
      <button className="btn btn-sm btn-outline-secondary" type="button" disabled={busy}
        onClick={() => onPageChange(result?.page ?? 1)}>Atkārtot</button>
    </p>}
    <div ref={listRef} className="example-scroll" role="region" aria-label={`${label}: piemēri`} tabIndex={0}>
      {selectedOnly ? <>
        {selectedOnPage.length === 0 && <p className="muted">Šajā korpusā nav atzīmētu piemēru.</p>}
        {selectedOnPage.map(renderSavedExample)}
      </> : <>
        {loading && <p className="muted">Ielādē piemērus...</p>}
        {result?.examples.length === 0 && <p className="muted">Piemēri netika atrasti.</p>}
        {result?.examples.map((example) => {
          const saved = examples.find((item) => item.tokenNumber === example.tokenNumber)
          const exampleText = <>{example.left}<strong>{example.match}</strong>{example.right}</>
          const toggleSelection = saved
            ? () => perform(() => onRemove(saved.id))
            : () => perform(() => selectExample(example))
          return <CorpusExampleRow key={example.tokenNumber} selected={saved !== undefined} busy={busy}
            sourceLabel={example.sourceLabel} sourceUrl={example.sourceUrl ?? result.url}
            originalSource={example.sourceUrl !== null} onToggle={editable && !saved?.approved ? toggleSelection : undefined}>
            {exampleText}
          </CorpusExampleRow>
        })}
      </>}
    </div>
    <div className="modal-footer">
      <Pagination page={selectedOnly ? currentSelectedPage : (result?.page ?? 1) - 1}
        totalPages={selectedOnly ? selectedPageCount : Math.ceil((result?.total ?? 0) / (result?.pageSize ?? 25))}
        disabled={busy} label={`${label}: lapošana`}
        onPageChange={(page) => selectedOnly ? setSelectedPage(page) : onPageChange(page + 1)} />
      <button className="btn btn-sm btn-light border" type="button" disabled={saving} onClick={onClose}>Aizvērt</button>
    </div>
  </Dialog>
}
