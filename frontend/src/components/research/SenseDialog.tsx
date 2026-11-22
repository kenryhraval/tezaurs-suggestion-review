import { useId, useState, type KeyboardEvent } from 'react'
import type { Example, Sense } from '../../api/types'
import { Dialog } from '../shared/Dialog'
import { ConfirmationDialog } from '../shared/ConfirmationDialog'
import { SenseExamples } from './SenseExamples'
import { ExampleEditDialog } from './ExampleEditDialog'

type Props = {
  sense: Sense | null
  initialTab: 'sense' | 'examples'
  examples: Example[]
  senses: Sense[]
  loading: boolean
  editable: boolean
  onSave: (gloss: string, approved: boolean, parentSenseId: string | null) => Promise<void>
  onDelete: () => Promise<void>
  onMerge: (targetSenseId: string) => Promise<void>
  onUpdateExample: (exampleId: string, senseId: string | null, approved: boolean) => Promise<void>
  onClose: () => void
}

export function SenseDialog({ sense, senses, examples, initialTab, loading, editable,
  onSave, onDelete, onMerge, onUpdateExample, onClose }: Props) {
  const [tab, setTab] = useState(initialTab)
  const tabsId = useId()
  const [editingExample, setEditingExample] = useState<Example | null>(null)
  const showingUnassignedExamples = sense === null && initialTab === 'examples'
  const [gloss, setGloss] = useState(sense?.gloss ?? '')
  const [parentSenseId, setParentSenseId] = useState(sense?.parentSenseId ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [confirmingDeletion, setConfirmingDeletion] = useState(false)
  const [mergeTargetId, setMergeTargetId] = useState<string | null>(null)
  const existing = sense !== null && sense.existingSenseId !== null
  const busy = saving || loading
  const mergeTargets = senses.filter((item) => item.id !== sense?.id)
  const mergeTarget = mergeTargets.find((item) => item.id === mergeTargetId)
  const parentOptions = senses.filter((item) => item.id !== sense?.id
    && item.parentSenseId === null)
  let title = 'Nozīme'
  if (!sense) title = showingUnassignedExamples ? 'Nepiesaistītie piemēri' : 'Pievienot nozīmi'
  if (existing) title = 'Esoša nozīme Tēzaurā'

  function changeTabWithKeyboard(event: KeyboardEvent<HTMLButtonElement>) {
    let nextTab: 'sense' | 'examples'
    if (event.key === 'ArrowLeft' || event.key === 'Home') {
      nextTab = 'sense'
    } else if (event.key === 'ArrowRight' || event.key === 'End') {
      nextTab = 'examples'
    } else {
      return
    }
    event.preventDefault()
    setTab(nextTab)
    document.getElementById(`${tabsId}-${nextTab}`)?.focus()
  }

  async function saveChange(action: () => Promise<void>) {
    if (busy) return
    setSaving(true)
    setError('')
    try {
      await action()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Darbību neizdevās saglabāt.')
    } finally {
      setSaving(false)
    }
  }

  async function saveSenseDetails() {
    if (!gloss.trim()) {
      setError('Norādiet skaidrojumu.')
      return
    }
    await onSave(gloss.trim(), sense?.approved ?? false, parentSenseId || null)
    setGloss(gloss.trim())
  }

  function saveExampleApproval(example: Example, approved: boolean) {
    return saveChange(() => onUpdateExample(example.id, example.senseId, approved))
  }

  function deleteSense() {
    return saveChange(async () => {
      await onDelete()
      onClose()
    })
  }

  function mergeSense() {
    if (!mergeTarget) return
    return saveChange(async () => {
      await onMerge(mergeTarget.id)
      onClose()
    })
  }

  async function moveExample(senseId: string | null, approved: boolean) {
    if (!editingExample) return
    await onUpdateExample(editingExample.id, senseId, approved)
    setEditingExample(null)
  }

  return <Dialog title={title} busy={busy} wide={sense !== null || showingUnassignedExamples} onCancel={onClose}>
    {sense && <div className="sense-dialog-tabs" role="tablist" aria-label="Nozīmes dati">
      <button type="button" role="tab" id={`${tabsId}-sense`} aria-controls={`${tabsId}-sense-panel`}
        aria-selected={tab === 'sense'} tabIndex={tab === 'sense' ? 0 : -1} disabled={busy}
        onClick={() => setTab('sense')} onKeyDown={changeTabWithKeyboard}>Nozīme</button>
      <button type="button" role="tab" id={`${tabsId}-examples`} aria-controls={`${tabsId}-examples-panel`}
        aria-selected={tab === 'examples'} tabIndex={tab === 'examples' ? 0 : -1} disabled={busy}
        onClick={() => setTab('examples')} onKeyDown={changeTabWithKeyboard}>Piemēri ({examples.length})</button>
    </div>}
    {!showingUnassignedExamples && <div id={`${tabsId}-sense-panel`} role={sense ? 'tabpanel' : undefined}
      aria-labelledby={sense ? `${tabsId}-sense` : undefined} hidden={tab !== 'sense'}>
      <label className="sense-dialog-gloss">
        Skaidrojums
        <textarea className="form-control form-control-sm" rows={4} maxLength={10000} autoFocus={initialTab === 'sense'}
          readOnly={existing || !editable} disabled={busy} value={gloss} onChange={(event) => setGloss(event.target.value)} />
      </label>
      {!existing && (parentOptions.length > 0 || parentSenseId) && <label>
        Virsnozīme
        <select className="custom-select custom-select-sm" value={parentSenseId} disabled={busy || !editable}
          onChange={(event) => setParentSenseId(event.target.value)}>
          <option value="">Nav – patstāvīga nozīme</option>
          {parentOptions.map((parent) => <option key={parent.id} value={parent.id}>
            {parent.existingSenseId !== null ? 'Tēzaurā: ' : ''}{parent.gloss}
          </option>)}
        </select>
      </label>}
      {sense && !existing && <div className="sense-additional-fields">
        <label>
          Apvienot ar citu nozīmi
          <select className="custom-select custom-select-sm" value={mergeTargetId ?? ''}
            disabled={busy || !editable || mergeTargets.length === 0}
            onChange={(event) => { setError(''); setMergeTargetId(event.target.value) }}>
            <option value="" disabled>Izvēlieties nozīmi</option>
            {mergeTargets.map((target) => <option key={target.id} value={target.id}>
              {target.existingSenseId !== null ? 'Tēzaurā: ' : ''}{target.gloss}
            </option>)}
          </select>
        </label>
      </div>}
    </div>}
    {(sense || showingUnassignedExamples) && <div id={`${tabsId}-examples-panel`} role={sense ? 'tabpanel' : undefined}
      aria-labelledby={sense ? `${tabsId}-examples` : undefined} hidden={tab !== 'examples'}>
      <SenseExamples examples={examples} busy={busy} editable={editable} canApprove={sense !== null && !existing}
        onApprovalChange={saveExampleApproval}
        onEdit={setEditingExample} />
    </div>}
    {error && !confirmingDeletion && !mergeTarget && <p className="error" role="alert">{error}</p>}
    <div className="modal-footer">
      {tab === 'sense' && sense && !existing && <button className="btn btn-sm btn-light border text-danger"
        type="button" disabled={busy || !editable}
        onClick={() => { setError(''); setConfirmingDeletion(true) }}>Dzēst</button>}
      <button type="button" className="btn btn-sm btn-secondary" disabled={busy} onClick={onClose}>Aizvērt</button>
      {tab === 'sense' && !existing && !showingUnassignedExamples && <button type="button" className="btn btn-sm btn-primary" disabled={busy || !editable}
        onClick={() => saveChange(saveSenseDetails)}>Saglabāt</button>}
    </div>
    {confirmingDeletion && <ConfirmationDialog busy={saving} error={error}
      message="Nozīme tiks dzēsta. Tās piemēri saglabāsies kā nepiesaistīti, un to apstiprinājumi tiks atcelti."
      onConfirm={deleteSense}
      onCancel={() => { setConfirmingDeletion(false); setError('') }} />}
    {mergeTarget && <ConfirmationDialog title="Apvienot nozīmes" busy={saving} error={error}
      message={`Šī nozīme tiks apvienota ar nozīmi “${mergeTarget.gloss}” un dzēsta. Piemēri un apakšnozīmes tiks pārvietoti; pārvietotie piemēri būs jāapstiprina no jauna. Mērķa skaidrojums, pazīmes un apstiprinājums paliks nemainīti.`}
      onConfirm={mergeSense}
      onCancel={() => { setMergeTargetId(null); setError('') }} />}
    {editingExample && <ExampleEditDialog example={editingExample} senses={senses}
      onSave={moveExample} onClose={() => setEditingExample(null)} />}
  </Dialog>
}
