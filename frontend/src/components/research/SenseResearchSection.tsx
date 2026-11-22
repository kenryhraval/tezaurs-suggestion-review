import { useEffect, useState } from 'react'
import {
  updateExample,
  deleteSense,
  mergeSenses,
  generateSenseDefinitions,
  getSenseModelOptions,
  getSenses,
  regroupSenses,
  saveSense,
} from '../../api/client'
import type { Example, Review, Sense, SenseModelOption, ModelOperation } from '../../api/types'
import { ConfirmationDialog } from '../shared/ConfirmationDialog'
import { SenseDialog } from './SenseDialog'
import { ModelPromptEditor } from '../shared/ModelPromptEditor'
import { ModelSelector } from '../shared/ModelSelector'

type Props = {
  review: Review
  senses: Sense[]
  examples: Example[]
  loading: boolean
  readError: string
  onSenseSaved: (sense: Sense) => void
  onSenseRemoved: (senseId: string) => void
  onSensesReplaced: (senses: Sense[]) => void
  onExampleSaved: (example: Example) => void
  editable: boolean
  defaultOpen: boolean
}

type OpenDialog = {
  tab: 'sense' | 'examples'
  senseId: string | null
}

export function SenseResearchSection({ review, senses, examples, loading, readError, editable, defaultOpen,
  onSenseSaved, onSenseRemoved, onSensesReplaced, onExampleSaved }: Props) {
  const [models, setModels] = useState<SenseModelOption[]>([])
  const [modelId, setModelId] = useState('')
  const [clusteringInstructions, setClusteringInstructions] = useState('')
  const [definitionInstructions, setDefinitionInstructions] = useState('')
  const [dialog, setDialog] = useState<OpenDialog | null>(null)
  const [modelRunning, setModelRunning] = useState(false)
  const [savingApproval, setSavingApproval] = useState(false)
  const [modelOperation, setModelOperation] = useState<ModelOperation | null>(null)
  const [error, setError] = useState('')
  const [expanded, setExpanded] = useState(defaultOpen)

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

  const selectedSense = senses.find((sense) => sense.id === dialog?.senseId) ?? null
  const unassignedExamples = examples.filter((example) => example.senseId === null)
  const shownExamples = dialog?.senseId === null ? unassignedExamples
    : examples.filter((example) => example.senseId === dialog?.senseId)
  const existingSenses = senses.filter((sense) => sense.existingSenseId !== null)
  const newSenses = senses.filter((sense) => sense.existingSenseId === null)
  const orderedSenses = [...existingSenses, ...newSenses]

  async function saveSelectedSense(gloss: string, approved: boolean, parentSenseId: string | null) {
    const saved = await saveSense(review.id, {
      id: selectedSense?.id,
      gloss,
      approved,
      parentSenseId,
    })
    if (!selectedSense) setDialog(null)
    onSenseSaved(saved)
  }

  async function toggleSenseApproval(sense: Sense) {
    setSavingApproval(true)
    setError('')
    try {
      const response = await saveSense(review.id, { ...sense, approved: !sense.approved })
      onSenseSaved(response)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās mainīt nozīmes apstiprinājumu.')
    } finally {
      setSavingApproval(false)
    }
  }

  async function deleteSelectedSense() {
    if (!selectedSense) return
    await deleteSense(review.id, selectedSense.id)
    onSenseRemoved(selectedSense.id)
  }

  async function mergeSelectedSense(targetSenseId: string) {
    if (!selectedSense) return
    await mergeSenses(review.id, selectedSense.id, targetSenseId)
    onSensesReplaced(await getSenses(review.id))
  }

  async function saveExampleChanges(exampleId: string, senseId: string | null, approved: boolean) {
    const savedExample = await updateExample(review.id, exampleId, senseId, approved)
    onExampleSaved(savedExample)
  }

  async function runModel() {
    if (!modelOperation) return
    setModelRunning(true)
    setError('')
    try {
      const updatedSenses = modelOperation === 'clustering'
        ? await regroupSenses(review.id, modelId, clusteringInstructions)
        : await generateSenseDefinitions(review.id, modelId, definitionInstructions)
      onSensesReplaced(updatedSenses)
      setModelOperation(null)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'MI darbību neizdevās pabeigt.')
    } finally {
      setModelRunning(false)
    }
  }

  const rootSenses = orderedSenses.filter((sense) => sense.parentSenseId === null)

  function renderSenseRow(sense: Sense, number: string) {
    const stateClass = sense.existingSenseId !== null ? ' sense-row-existing' : ''
    return <div key={sense.id} className={`sense-row${stateClass}`}>
      {sense.existingSenseId === null && <button type="button" className="sense-visibility icon-button" aria-pressed={sense.approved}
        disabled={!editable || loading || modelRunning || savingApproval}
        title={sense.approved ? 'Paslēpt melnrakstā' : 'Rādīt melnrakstā'}
        aria-label={sense.approved ? 'Paslēpt melnrakstā' : 'Rādīt melnrakstā'}
        onClick={() => toggleSenseApproval(sense)}>
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true">
          <path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" />
          <circle cx="12" cy="12" r="3" />
          {!sense.approved && <path d="m3 3 18 18" />}
        </svg>
      </button>}
      <button type="button" className="sense-row-open" disabled={loading || modelRunning || savingApproval} aria-haspopup="dialog"
        onClick={() => setDialog({ tab: 'sense', senseId: sense.id })}>
        {sense.approved && <span className="sr-only">Apstiprināta nozīme.</span>}
        {sense.existingSenseId !== null && <span className="sr-only">Esoša nozīme Tēzaurā.</span>}
        <span className="sense-row-number">{number}.</span>
        <span className="sense-row-description">{sense.gloss}</span>
      </button>
      <span className="sense-row-examples">{sense.exampleIds.length} piemēri</span>
    </div>
  }

  return <section className={`review-section sense-research${expanded ? ' is-open' : ''}`}>
    <details open={expanded} onToggle={(event) => setExpanded(event.currentTarget.open)}>
      <summary className="section-title">4. Nozīmju izpēte</summary>
      {loading && senses.length === 0 && <p className="muted">Ielādē nozīmes...</p>}
      {!loading && !readError && senses.length === 0 && <p className="muted">Nozīmes vēl nav pievienotas.</p>}
      <div className="sense-groups">
        {rootSenses.map((parent, parentIndex) => {
          const children = orderedSenses.filter((sense) => sense.parentSenseId === parent.id)
          return <div className="sense-group" key={parent.id}>
            {renderSenseRow(parent, String(parentIndex + 1))}
            {children.length > 0 && <div className="sense-subsenses">
              {children.map((sense, childIndex) => renderSenseRow(sense, `${parentIndex + 1}.${childIndex + 1}`))}
            </div>}
          </div>
        })}
      </div>
      <div className="sense-workspace-actions">
        <button type="button" className="sense-text-action" disabled={!editable || loading || modelRunning}
          onClick={() => setDialog({ tab: 'sense', senseId: null })}>Pievienot nozīmi</button>
        {unassignedExamples.length > 0 && <button type="button" className="sense-text-action"
          disabled={loading || modelRunning} onClick={() => setDialog({ tab: 'examples', senseId: null })}>
          Nepiesaistītie piemēri
        </button>}
      </div>
      <div className="sense-research-actions">
        <ModelSelector models={models} value={modelId} disabled={!editable || loading || modelRunning || models.length === 0}
          onChange={setModelId} />
        <div className="sense-model-actions">
          <button className="btn btn-sm btn-outline-secondary" type="button"
            disabled={!editable || loading || modelRunning || !modelId}
            onClick={() => { setError(''); setModelOperation('clustering') }}>Grupēt piemērus</button>
          <button className="btn btn-sm btn-outline-secondary" type="button" disabled={!editable || loading || modelRunning || !modelId}
            onClick={() => { setError(''); setModelOperation('definitions') }}>Piedāvāt skaidrojumus</button>
        </div>
      </div>
      {modelRunning && <p className="muted" role="status">
        {modelOperation === 'clustering' ? 'Grupē piemērus...' : 'Ģenerē skaidrojumus...'}
      </p>}
      {error && !modelOperation && <p className="error" role="alert">{error}</p>}
    </details>
    {dialog && <SenseDialog key={dialog.senseId ?? (dialog.tab === 'sense' ? 'new' : 'unassigned')}
      initialTab={dialog.tab} sense={selectedSense} senses={orderedSenses} examples={shownExamples}
      loading={loading || modelRunning} editable={editable}
      onSave={saveSelectedSense} onDelete={deleteSelectedSense} onMerge={mergeSelectedSense}
      onUpdateExample={saveExampleChanges} onClose={() => setDialog(null)} />}
    {modelOperation && <ConfirmationDialog busy={modelRunning} error={error}
      title={modelOperation === 'clustering' ? 'Grupēt piemērus' : 'Piedāvāt skaidrojumus'}
      message={modelOperation === 'clustering'
        ? 'MI piedāvās jaunas, neapstiprinātas nozīmes un apakšnozīmes un pārgrupēs neapstiprinātos piemērus. Apstiprinātie piemēri un esošās nozīmes saglabāsies.'
        : 'Vispirms grupējiet piemērus, pārbaudiet dalījumu pa nozīmēm un apstipriniet izvēlētos piemērus. MI vienā pieprasījumā izveidos skaidrojumus visām jaunajām nozīmēm ar apstiprinātiem piemēriem un aizstās to pašreizējos skaidrojumus. Nozīmes bez apstiprinātiem piemēriem netiks mainītas. Esošās Tēzaura nozīmes un tām piesaistītie piemēri tiks izmantoti kontekstam.'}
      onConfirm={runModel} onCancel={() => { setModelOperation(null); setError('') }}>
      <ModelPromptEditor key={modelOperation} operation={modelOperation}
        additionalInstructions={modelOperation === 'clustering' ? clusteringInstructions : definitionInstructions}
        disabled={modelRunning} onChange={modelOperation === 'clustering' ? setClusteringInstructions : setDefinitionInstructions} />
    </ConfirmationDialog>}
  </section>
}
