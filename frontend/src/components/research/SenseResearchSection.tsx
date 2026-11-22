import { useEffect, useState } from 'react'
import {
  assignExampleSense,
  deleteSense,
  generateSenseDefinitions,
  getExamplesForCase,
  getSenseModelOptions,
  getSenses,
  regroupSenses,
  saveSense,
} from '../../api/client'
import type { Example, Review, Sense, SenseModelOption } from '../../api/types'
import { ConfirmationDialog } from '../shared/ConfirmationDialog'
import { SenseDialog } from './SenseDialog'

type Props = {
  review: Review
  examplesRevision: number
  onSensesChanged: () => void
}

const NEW_SENSE = 'new'
const UNASSIGNED = 'unassigned'

export function SenseResearchSection({ review, examplesRevision, onSensesChanged }: Props) {
  const [senses, setSenses] = useState<Sense[]>([])
  const [examples, setExamples] = useState<Example[]>([])
  const [models, setModels] = useState<SenseModelOption[]>([])
  const [modelId, setModelId] = useState('')
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [loadedRevision, setLoadedRevision] = useState<number | null>(null)
  const loading = loadedRevision !== examplesRevision
  const [regrouping, setRegrouping] = useState(false)
  const [confirmingRegroup, setConfirmingRegroup] = useState(false)
  const [error, setError] = useState('')
  const [expanded, setExpanded] = useState(true)

  useEffect(() => {
    let requestIsOutdated = false
    getSenseModelOptions().then((loaded) => {
      if (requestIsOutdated) return
      setModels(loaded)
      setModelId(loaded.find((model) => model.defaultModel)?.id ?? loaded[0]?.id ?? '')
    }).catch((cause) => {
      if (!requestIsOutdated) setError(cause instanceof Error ? cause.message : 'Neizdevās ielādēt MI modeļus.')
    })
    return () => { requestIsOutdated = true }
  }, [])

  useEffect(() => {
    let requestIsOutdated = false
    async function loadWorkspace() {
      try {
        const [loadedSenses, loadedExamples] = await Promise.all([getSenses(review.id), getExamplesForCase(review.id)])
        if (requestIsOutdated) return
        const senseIds = new Set(loadedSenses.map((sense) => sense.id))
        setSenses(loadedSenses)
        setExamples(loadedExamples.filter((example) => example.senseId === null || senseIds.has(example.senseId)))
      } catch (cause) {
        if (!requestIsOutdated) setError(cause instanceof Error ? cause.message : 'Neizdevās ielādēt nozīmes.')
      } finally {
        if (!requestIsOutdated) setLoadedRevision(examplesRevision)
      }
    }
    loadWorkspace()
    return () => { requestIsOutdated = true }
  }, [review.id, examplesRevision])

  const selectedSense = senses.find((sense) => sense.id === selectedId) ?? null
  const unassignedExamples = examples.filter((example) => example.senseId === null)
  const shownExamples = selectedId === UNASSIGNED ? unassignedExamples
    : examples.filter((example) => example.senseId === selectedId)
  const existingSenses = senses.filter((sense) => sense.existingSenseId !== null)
  const newSenses = senses.filter((sense) => sense.existingSenseId === null)
  const orderedSenses = [...existingSenses, ...newSenses]

  async function saveSelectedSense(gloss: string, approved: boolean) {
    await saveSense(review.id, {
      id: selectedSense?.id,
      gloss,
      approved,
      parentDraftSenseId: selectedSense?.parentDraftSenseId ?? null,
      parentTezaursSenseId: selectedSense?.parentTezaursSenseId ?? null,
    })
    if (selectedId === NEW_SENSE) setSelectedId(null)
    onSensesChanged()
  }

  async function deleteSelectedSense() {
    if (!selectedSense) return
    await deleteSense(review.id, selectedSense.id)
    onSensesChanged()
  }

  async function transferExample(exampleId: string, senseId: string | null) {
    await assignExampleSense(review.id, exampleId, senseId)
    onSensesChanged()
  }

  async function proposeDefinition(description: string) {
    if (!selectedSense) return ''
    const definitions = await generateSenseDefinitions(review.id, [{
      id: selectedSense.id,
      description,
      exampleIds: shownExamples.map((example) => example.id),
    }], modelId)
    const result = definitions.find((definition) => definition.senseId === selectedSense.id)
    if (!result) throw new Error('MI neatgrieza skaidrojumu izvēlētajai nozīmei.')
    return result.definition
  }

  async function regroup() {
    setRegrouping(true)
    setError('')
    try {
      await regroupSenses(review.id, modelId)
      setConfirmingRegroup(false)
      onSensesChanged()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās pārgrupēt piemērus.')
    } finally {
      setRegrouping(false)
    }
  }

  return <section className={`review-section sense-research${expanded ? ' is-open' : ''}`}>
    <details open={expanded} onToggle={(event) => setExpanded(event.currentTarget.open)}>
      <summary className="section-title">4. Nozīmju izpēte</summary>
      {loading && senses.length === 0 && <p className="muted">Ielādē nozīmes...</p>}
      <div className="sense-cards">
        {orderedSenses.map((sense) => <button type="button" key={sense.id}
          className={`sense-card${sense.existingSenseId !== null ? ' sense-card-existing' : sense.approved ? ' sense-card-approved' : ''}`}
          disabled={loading || regrouping} onClick={() => setSelectedId(sense.id)}>
          <span className="sense-card-gloss">{sense.gloss}</span>
          <small className="muted">
            {sense.existingSenseId !== null ? 'Tēzaurā · ' : sense.approved ? 'Apstiprināta · ' : ''}
            {sense.exampleIds.length} piemēri
          </small>
        </button>)}
        {unassignedExamples.length > 0 && <button type="button" className="sense-card sense-card-unassigned"
          disabled={loading || regrouping} onClick={() => setSelectedId(UNASSIGNED)}>
          <span>Nepiesaistītie piemēri</span><small className="muted">{unassignedExamples.length} piemēri</small>
        </button>}
        <button type="button" className="sense-card sense-card-add" disabled={loading || regrouping}
          onClick={() => setSelectedId(NEW_SENSE)}>＋ Pievienot nozīmi</button>
      </div>
      <div className="sense-research-actions">
        <select className="custom-select custom-select-sm sense-model-select" aria-label="MI modelis"
          value={modelId} disabled={loading || regrouping || models.length === 0}
          onChange={(event) => setModelId(event.target.value)}>
          {models.length === 0 && <option value="">MI modeļi nav pieejami</option>}
          {models.map((model) => <option key={model.id} value={model.id}>{model.label}</option>)}
        </select>
        <button className="btn btn-sm btn-outline-secondary" type="button"
          disabled={loading || regrouping || !modelId || examples.length === 0}
          onClick={() => { setError(''); setConfirmingRegroup(true) }}>Pārgrupēt ar MI</button>
      </div>
      {regrouping && <p className="muted" role="status">Grupē piemērus...</p>}
      {error && !confirmingRegroup && <p className="error" role="alert">{error}</p>}
    </details>
    {selectedId !== null && (selectedSense || selectedId === NEW_SENSE || selectedId === UNASSIGNED) && <SenseDialog
      key={selectedId} mode={selectedSense ? 'sense' : selectedId === NEW_SENSE ? 'create' : 'unassigned'}
      sense={selectedSense} senses={orderedSenses} examples={shownExamples}
      initialGloss={review.sources[0]?.submittedDefinition ?? ''}
      loading={loading || regrouping} canGenerate={modelId !== ''}
      onSave={saveSelectedSense} onDelete={deleteSelectedSense} onTransfer={transferExample}
      onGenerate={proposeDefinition} onClose={() => setSelectedId(null)} />}
    {confirmingRegroup && <ConfirmationDialog busy={regrouping} error={error}
      message="MI izveidos jaunas, neapstiprinātas nozīmes un pārvietos sagrupētos piemērus, arī no apstiprinātām nozīmēm. Esošās nozīmes un to apstiprinājumi saglabāsies. Nesagrupētie piemēri paliks pašreizējās vietās."
      onConfirm={regroup} onCancel={() => { setConfirmingRegroup(false); setError('') }} />}
  </section>
}
