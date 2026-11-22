import { useState } from 'react'
import type { Example, Sense } from '../../api/types'
import { Dialog } from '../shared/Dialog'
import { ConfirmationDialog } from '../shared/ConfirmationDialog'
import { UsageSourceLink } from '../usage/UsageSourceLink'

type Props = {
  mode: 'create' | 'sense' | 'unassigned'
  sense: Sense | null
  senses: Sense[]
  examples: Example[]
  initialGloss: string
  loading: boolean
  canGenerate: boolean
  onSave: (gloss: string, approved: boolean) => Promise<void>
  onDelete: () => Promise<void>
  onTransfer: (exampleId: string, senseId: string | null) => Promise<void>
  onGenerate: (description: string) => Promise<string>
  onClose: () => void
}

export function SenseDialog({ mode, sense, senses, examples, initialGloss, loading,
  canGenerate, onSave, onDelete, onTransfer, onGenerate, onClose }: Props) {
  const [gloss, setGloss] = useState(sense?.gloss ?? initialGloss)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [confirmingDeletion, setConfirmingDeletion] = useState(false)
  const existing = sense?.existingSenseId !== null && sense?.existingSenseId !== undefined
  const busy = saving || loading
  const hasChildren = sense !== null && senses.some((item) => item.parentDraftSenseId === sense.id)
  let title = 'Nozīme'
  if (mode === 'create') title = 'Pievienot nozīmi'
  if (mode === 'unassigned') title = 'Nepiesaistītie piemēri'
  if (existing) title = 'Esoša nozīme Tēzaurā'

  async function perform(action: () => Promise<void>) {
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

  async function save(approved: boolean) {
    if (!gloss.trim()) {
      setError('Norādiet skaidrojumu.')
      return
    }
    await onSave(gloss.trim(), approved)
    setGloss(gloss.trim())
  }

  function senseLabel(item: Sense) {
    let prefix = ''
    if (item.existingSenseId !== null) prefix = 'Tēzaurā: '
    else if (item.approved) prefix = 'Apstiprināta: '
    return prefix + item.gloss
  }

  return <Dialog title={title} busy={busy} wide onCancel={onClose}>
    {mode !== 'unassigned' && <label className="sense-dialog-gloss">
      Skaidrojums
      <textarea className="form-control form-control-sm" rows={4} maxLength={10000} autoFocus
        readOnly={existing} disabled={busy} value={gloss} onChange={(event) => setGloss(event.target.value)} />
    </label>}
    {sense?.approved && !existing && <p className="muted">Apstiprināta — redzama labojumu melnrakstā.</p>}
    {mode !== 'create' && <div className="sense-dialog-examples">
      {examples.length === 0 && <p className="muted">Piemēri nav piesaistīti.</p>}
      {examples.map((example) => <article className="usage-evidence" key={example.id}>
        <p>{example.citation}</p>
        <div className="sense-example-actions">
          <UsageSourceLink label={example.sourceLabel} url={example.sourceUrl ?? example.url}
            originalSource={example.sourceUrl !== null} />
          <select className="custom-select custom-select-sm" aria-label="Piemēra nozīme" disabled={busy}
            value={example.senseId ?? ''}
            onChange={(event) => perform(() => onTransfer(example.id, event.target.value || null))}>
            <option value="">Nav piesaistīts</option>
            {senses.map((item) => <option key={item.id} value={item.id}>{senseLabel(item)}</option>)}
          </select>
        </div>
      </article>)}
    </div>}
    {error && !confirmingDeletion && <p className="error" role="alert">{error}</p>}
    <div className="modal-footer">
      {sense && !existing && <button type="button" className="btn btn-sm btn-light border text-danger"
        disabled={busy || hasChildren} onClick={() => setConfirmingDeletion(true)}>Dzēst</button>}
      {sense && !existing && canGenerate && <button type="button" className="btn btn-sm btn-outline-secondary"
        disabled={busy || examples.length === 0}
        onClick={() => perform(async () => setGloss(await onGenerate(gloss)))}>Piedāvāt skaidrojumu ar MI</button>}
      <button type="button" className="btn btn-sm btn-secondary" disabled={busy} onClick={onClose}>Aizvērt</button>
      {mode !== 'unassigned' && !existing && <>
        {sense && <button type="button" className="btn btn-sm btn-outline-secondary" disabled={busy}
          onClick={() => perform(() => save(!sense.approved))}>
          {sense.approved ? 'Atcelt apstiprinājumu' : 'Apstiprināt'}
        </button>}
        <button type="button" className="btn btn-sm btn-primary" disabled={busy}
          onClick={() => perform(() => save(sense?.approved ?? false))}>Saglabāt</button>
      </>}
    </div>
    {confirmingDeletion && <ConfirmationDialog busy={saving} error={error}
      message="Nozīme tiks dzēsta. Tās piemēri saglabāsies kā nepiesaistīti."
      onConfirm={() => perform(async () => { await onDelete(); onClose() })}
      onCancel={() => { setConfirmingDeletion(false); setError('') }} />}
  </Dialog>
}
