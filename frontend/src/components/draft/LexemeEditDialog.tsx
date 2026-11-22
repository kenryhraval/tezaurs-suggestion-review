import { useState } from 'react'
import type { LexemeDraft, SaveLexemeDraft, TezaursFormOptions } from '../../api/types'
import { EditDialog } from '../shared/EditDialog'
import { FlagEditor } from './FlagEditor'
import { parseFlags, parseMultipleValues, lexemeRequest } from './formValues'

type Props = {
  initialLexeme: LexemeDraft
  options: TezaursFormOptions
  editable: boolean
  onSave: (request: SaveLexemeDraft) => Promise<void>
  onClose: () => void
}

const stemFields = [
  { name: 'stem1', label: 'Pamatformas celms' },
  { name: 'stem2', label: 'Tagadnes celms' },
  { name: 'stem3', label: 'Pagātnes celms' },
] as const

export function LexemeEditDialog({ initialLexeme, options, editable, onSave, onClose }: Props) {
  const [lexeme, setLexeme] = useState(initialLexeme)
  const [pronunciations, setPronunciations] = useState(initialLexeme.pronunciations.join(' | '))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const paradigm = options.paradigms.find((item) => item.id === lexeme.paradigmId)

  async function save() {
    setSaving(true)
    setError('')
    try {
      await onSave(lexemeRequest({ ...lexeme, pronunciations: parseMultipleValues(pronunciations),
        flags: parseFlags(lexeme.flags, options.flags) }))
      onClose()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt leksēmu.')
    } finally {
      setSaving(false)
    }
  }

  return <EditDialog title={editable ? (initialLexeme.id ? 'Labot leksēmu' : 'Pievienot leksēmu') : 'Leksēmas dati'}
    readOnly={!editable} busy={saving} error={error} onSubmit={save} onCancel={onClose}>
    <label>Pamatforma
      <input className="form-control form-control-sm" autoFocus maxLength={255} required value={lexeme.lemma}
        onChange={(event) => setLexeme({ ...lexeme, lemma: event.target.value })} />
    </label>
    <label>Loma šķirklī
      <select className="custom-select custom-select-sm" value={lexeme.roleId}
        onChange={(event) => setLexeme({ ...lexeme, roleId: Number(event.target.value) })}>
        {options.lexemeRoles.map((role) => <option key={role.id} value={role.id}>{role.label || role.code}</option>)}
      </select>
    </label>
    <label>Paradigma
      <select className="custom-select custom-select-sm" value={lexeme.paradigmId ?? ''}
        onChange={(event) => setLexeme({ ...lexeme, paradigmId: event.target.value ? Number(event.target.value) : null })}>
        <option value="">Nav izvēlēta</option>
        {options.paradigms.map((item) => <option key={item.id} value={item.id}>{item.code}: {item.label}</option>)}
      </select>
    </label>
    {paradigm && <div className="stem-fields">
      {stemFields.slice(0, Math.max(1, paradigm.stems)).map((field) => <label key={field.name}>{field.label}
        <input className="form-control form-control-sm" value={lexeme[field.name] ?? ''}
          onChange={(event) => setLexeme({ ...lexeme, [field.name]: event.target.value || null })} />
      </label>)}
    </div>}
    <label>Izrunas varianti
      <input className="form-control form-control-sm" value={pronunciations} onChange={(event) => setPronunciations(event.target.value)} />
    </label>
    <FlagEditor definitions={options.flags} flags={lexeme.flags} onChange={(flags) => setLexeme({ ...lexeme, flags })} scope="L" />
  </EditDialog>
}
