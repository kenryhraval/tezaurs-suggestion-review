import { useState } from 'react'
import type { EntryDraft, EntryType, SaveEntryDraft, TezaursFormOptions } from '../../api/types'
import { EditDialog } from '../shared/EditDialog'
import { FlagEditor } from './FlagEditor'
import { parseFlags } from './formValues'

type Props = {
  entry: EntryDraft
  options: TezaursFormOptions
  editable: boolean
  onSave: (request: SaveEntryDraft) => Promise<void>
  onClose: () => void
}

export function EntryEditDialog({ entry, options, editable, onSave, onClose }: Props) {
  const [form, setForm] = useState<SaveEntryDraft>({ heading: entry.heading, type: entry.type,
    etymology: entry.etymology, normativeComment: entry.normativeComment, scientificName: entry.scientificName,
    source: entry.source, notes: entry.notes, flags: entry.flags })
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  async function save() {
    setSaving(true)
    setError('')
    try {
      await onSave({ ...form, flags: parseFlags(form.flags, options.flags) })
      onClose()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt šķirkli.')
    } finally {
      setSaving(false)
    }
  }

  return <EditDialog title={editable ? 'Labot šķirkli' : 'Šķirkļa dati'} readOnly={!editable}
    busy={saving} error={error} onSubmit={save} onCancel={onClose}>
    <fieldset>
      <legend>Šķirklis</legend>
      <label>Šķirkļa vārds
        <input className="form-control form-control-sm" autoFocus maxLength={255} required value={form.heading}
          onChange={(event) => setForm({ ...form, heading: event.target.value })} />
      </label>
      <label>Šķirkļa tips
        <select className="custom-select custom-select-sm" value={form.type}
          onChange={(event) => setForm({ ...form, type: event.target.value as EntryType })}>
          <option value="WORD">Vārds</option>
          <option value="MULTIWORD_EXPRESSION">Stabils vārdu savienojums</option>
          <option value="WORD_PART">Vārda daļa</option>
        </select>
      </label>
      <FlagEditor definitions={options.flags} flags={form.flags} onChange={(flags) => setForm({ ...form, flags })} scope="E" />
    </fieldset>
    <fieldset>
      <legend>Papildu informācija</legend>
      <label>Cilme<textarea className="form-control form-control-sm" rows={2} value={form.etymology ?? ''}
        onChange={(event) => setForm({ ...form, etymology: event.target.value })} /></label>
      <label>Normatīvais komentārs<textarea className="form-control form-control-sm" rows={2} value={form.normativeComment ?? ''}
        onChange={(event) => setForm({ ...form, normativeComment: event.target.value })} /></label>
      <label>Zinātniskais nosaukums<input className="form-control form-control-sm" value={form.scientificName ?? ''}
        onChange={(event) => setForm({ ...form, scientificName: event.target.value })} /></label>
      <label>Avots<input className="form-control form-control-sm" value={form.source ?? ''}
        onChange={(event) => setForm({ ...form, source: event.target.value })} /></label>
      <label>Iekšējās piezīmes<textarea className="form-control form-control-sm" rows={3} value={form.notes ?? ''}
        onChange={(event) => setForm({ ...form, notes: event.target.value })} /></label>
    </fieldset>
  </EditDialog>
}
