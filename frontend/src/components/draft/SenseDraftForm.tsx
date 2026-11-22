import { useState } from 'react'
import { saveSense } from '../../api/client'
import type { Sense, TezaursFormOptions } from '../../api/types'
import { EditDialog } from '../shared/EditDialog'
import { EditButton } from '../shared/EditButton'
import { SenseFields } from './SenseFields'
import { parseFlags } from './formValues'

type Props = {
  reviewId: string
  senses: Sense[]
  loading: boolean
  options: TezaursFormOptions | null
  editable: boolean
  onSenseSaved: (sense: Sense) => void
}

export function SenseDraftForm({ reviewId, senses, loading, options, editable, onSenseSaved }: Props) {
  const [editingSense, setEditingSense] = useState<Sense | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const drafts = senses.filter((sense) => sense.approved && sense.existingSenseId === null)
  const existingRoots = senses.filter((sense) => sense.existingSenseId !== null && sense.parentSenseId === null)
  const newRoots = drafts.filter((sense) => sense.parentSenseId === null)

  async function saveEditedSense() {
    if (!editingSense || !options) return
    setSaving(true)
    setError('')
    try {
      const saved = await saveSense(reviewId, { ...editingSense, flags: parseFlags(editingSense.flags, options.flags) })
      onSenseSaved(saved)
      setEditingSense(null)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt nozīmi.')
    } finally {
      setSaving(false)
    }
  }

  function renderSense(sense: Sense, number: string) {
    return <>
      <span className="entity-actions">
        <EditButton className={editable ? 'btn btn-sm btn-success' : 'btn btn-sm btn-secondary'}
          icon={editable ? 'edit' : 'info'} label={editable ? 'Labot nozīmi un pazīmes' : 'Skatīt nozīmes datus'}
          disabled={saving || !options} onClick={() => { setError(''); setEditingSense(sense) }} />
      </span>
      <strong>{number}.</strong>{' '}{sense.gloss}
    </>
  }

  return <section aria-label="Nozīmes">
    {loading && <p className="muted">Ielādē nozīmes...</p>}
    {existingRoots.map((parent, parentIndex) => {
      const additions = drafts.filter((sense) => sense.parentSenseId === parent.id)
      const existingChildCount = senses.filter((sense) => sense.existingSenseId !== null && sense.parentSenseId === parent.id).length
      return additions.map((sense, childIndex) => <div className="draft-sense" key={sense.id}>
        {renderSense(sense, `${parentIndex + 1}.${existingChildCount + childIndex + 1}`)}
      </div>)
    })}
    {newRoots.map((parent, parentIndex) => {
      const number = existingRoots.length + parentIndex + 1
      const children = drafts.filter((sense) => sense.parentSenseId === parent.id)
      return <div className="draft-sense" key={parent.id}>
        {renderSense(parent, String(number))}
        {children.map((sense, childIndex) => <div className="draft-sense" key={sense.id}>
          {renderSense(sense, `${number}.${childIndex + 1}`)}
        </div>)}
      </div>
    })}
    {!loading && drafts.length === 0 && <p className="muted">Nav apstiprinātu jaunu nozīmju.</p>}
    {editingSense && options && <EditDialog title={editable ? 'Labot nozīmi un pazīmes' : 'Nozīmes dati'}
      busy={saving} readOnly={!editable} error={error} onSubmit={saveEditedSense}
      onCancel={() => { setEditingSense(null); setError('') }}>
      <SenseFields sense={editingSense} options={options}
        onChange={(updated) => setEditingSense({ ...editingSense, ...updated })} />
    </EditDialog>}
  </section>
}
