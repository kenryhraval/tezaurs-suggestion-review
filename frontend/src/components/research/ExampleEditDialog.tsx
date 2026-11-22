import { useState } from 'react'
import type { Example, Sense } from '../../api/types'
import { EditDialog } from '../shared/EditDialog'

type Props = {
  example: Example
  senses: Sense[]
  onSave: (senseId: string | null, approved: boolean) => Promise<void>
  onClose: () => void
}

export function ExampleEditDialog({ example, senses, onSave, onClose }: Props) {
  const [senseId, setSenseId] = useState(example.senseId ?? '')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  async function save() {
    setSaving(true)
    setError('')
    try {
      const targetSenseId = senseId || null
      const targetSense = senses.find((sense) => sense.id === targetSenseId)
      const approved = targetSenseId === example.senseId && example.approved && targetSense?.existingSenseId === null
      await onSave(targetSenseId, approved)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās pārvietot piemēru.')
    } finally {
      setSaving(false)
    }
  }

  return <EditDialog title="Piemēra nozīme" busy={saving} error={error} wide={false}
    onSubmit={save} onCancel={onClose}>
    <label>
      Nozīme
      <select className="custom-select custom-select-sm" value={senseId} disabled={saving}
        onChange={(event) => setSenseId(event.target.value)}>
        <option value="">Nav piesaistīts</option>
        {senses.map((sense) => <option key={sense.id} value={sense.id}>
          {sense.existingSenseId !== null ? 'Tēzaurā: ' : ''}{sense.gloss}
        </option>)}
      </select>
    </label>
    {example.approved && <p className="muted">Mainot nozīmi, piemērs būs jāapstiprina no jauna.</p>}
  </EditDialog>
}
