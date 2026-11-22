import { useEffect, useState, type ReactNode } from 'react'
import { createLexemeDraft, deleteLexemeDraft, getEntryDraft, updateEntryDraft, updateLexemeDraft } from '../../api/client'
import type { EntryDraft, LexemeDraft, SaveEntryDraft, SaveLexemeDraft, TezaursFormOptions } from '../../api/types'
import { EntryHeading } from '../tezaurs/EntryHeading'
import { EntryActions, LexemeActions } from '../tezaurs/TezaursActions'
import { ConfirmationDialog } from '../shared/ConfirmationDialog'
import { EntryEditDialog } from './EntryEditDialog'
import { LexemeEditDialog } from './LexemeEditDialog'
import { lexemeRequest } from './formValues'

type Props = {
  reviewId: string
  options: TezaursFormOptions | null
  children?: ReactNode
  defaultOpen: boolean
  complete: boolean
  editable: boolean
}

export function EntryDraftForm({ reviewId, options, children, editable, defaultOpen, complete }: Props) {
  const [expanded, setExpanded] = useState(defaultOpen)
  const [entry, setEntry] = useState<EntryDraft | null>(null)
  const [loading, setLoading] = useState(true)
  const [editingEntry, setEditingEntry] = useState(false)
  const [editingLexeme, setEditingLexeme] = useState<LexemeDraft | null>(null)
  const [deletingLexeme, setDeletingLexeme] = useState<LexemeDraft | null>(null)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let outdated = false
    async function loadEntry() {
      try {
        const saved = await getEntryDraft(reviewId)
        if (!outdated) setEntry(saved)
      } catch (cause) {
        if (!outdated) setError(cause instanceof Error ? cause.message : 'Neizdevās ielādēt šķirkli.')
      } finally {
        if (!outdated) setLoading(false)
      }
    }
    loadEntry()
    return () => { outdated = true }
  }, [reviewId])

  function applySavedLexeme(saved: LexemeDraft) {
    setEntry((current) => current ? { ...current,
      lexemes: current.lexemes.some((lexeme) => lexeme.id === saved.id)
        ? current.lexemes.map((lexeme) => lexeme.id === saved.id ? saved : { ...lexeme, primary: saved.primary ? false : lexeme.primary })
        : [...current.lexemes.map((lexeme) => ({ ...lexeme, primary: saved.primary ? false : lexeme.primary })), saved],
    } : current)
  }

  async function saveEntry(request: SaveEntryDraft) {
    setEntry(await updateEntryDraft(reviewId, request))
  }

  async function saveLexeme(request: SaveLexemeDraft) {
    if (!editingLexeme) return
    const saved = editingLexeme.id
      ? await updateLexemeDraft(reviewId, editingLexeme.id, request)
      : await createLexemeDraft(reviewId, request)
    applySavedLexeme(saved)
  }

  async function selectPrimary(id: string) {
    const lexeme = entry?.lexemes.find((item) => item.id === id)
    if (!lexeme || lexeme.primary) return
    setSaving(true)
    setError('')
    try {
      applySavedLexeme(await updateLexemeDraft(reviewId, id, lexemeRequest({ ...lexeme, primary: true })))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās mainīt galveno leksēmu.')
    } finally {
      setSaving(false)
    }
  }

  function addLexeme() {
    if (!options) return
    setEditingLexeme({ id: null, lemma: '', primary: false, roleId: options.lexemeRoles[0]?.id ?? 1,
      paradigmId: null, stem1: null, stem2: null, stem3: null, pronunciations: [], flags: {} })
  }

  async function confirmLexemeDeletion() {
    if (!deletingLexeme?.id) return
    setSaving(true)
    setError('')
    try {
      await deleteLexemeDraft(reviewId, deletingLexeme.id)
      setEntry((current) => current ? { ...current, lexemes: current.lexemes.filter((lexeme) => lexeme.id !== deletingLexeme.id) } : current)
      setDeletingLexeme(null)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās dzēst leksēmu.')
    } finally {
      setSaving(false)
    }
  }

  return <details open={expanded} onToggle={(event) => setExpanded(event.currentTarget.open)}
    className="entry-draft-section existing-entry review-section">
    <summary className={!expanded && complete ? 'section-title confirmed' : 'section-title'}>5. Labojumu melnraksts</summary>
    {loading && <p className="muted">Ielādē šķirkli...</p>}
    {!loading && !entry && !error && <p className="muted">Šķirkļa melnraksts vēl nav izveidots.</p>}
    {error && !deletingLexeme && <p className="error" role="alert">{error}</p>}
    {entry && <>
      <div className="draft-heading">
        <EntryActions onEdit={options && !saving ? () => setEditingEntry(true) : undefined}
          onAddLexeme={editable && options && !saving ? addLexeme : undefined} readOnly={!editable} />
        <EntryHeading heading={entry.heading} humanKey={entry.humanKey} />
      </div>
      {entry.lexemes.length > 1 && <label>Galvenā leksēma
        <select className="custom-select custom-select-sm" disabled={!editable || saving}
          value={entry.lexemes.find((lexeme) => lexeme.primary)?.id ?? ''} onChange={(event) => selectPrimary(event.target.value)}>
          {entry.lexemes.map((lexeme) => <option key={lexeme.id} value={lexeme.id ?? ''}>{lexeme.lemma}</option>)}
        </select>
      </label>}
      {entry.lexemes.map((lexeme) => <div className="draft-lexeme" key={lexeme.id}>
        <LexemeActions onEdit={options && !saving ? () => setEditingLexeme(lexeme) : undefined} readOnly={!editable}
          onDelete={editable && !saving && entry.lexemes.length > 1 && !lexeme.primary
            ? () => { setError(''); setDeletingLexeme(lexeme) } : undefined} />
        <strong>{lexeme.lemma}</strong>
      </div>)}
    </>}
    {children}
    {editingEntry && entry && options && <EntryEditDialog entry={entry} options={options} editable={editable}
      onSave={saveEntry} onClose={() => setEditingEntry(false)} />}
    {editingLexeme && options && <LexemeEditDialog initialLexeme={editingLexeme} options={options} editable={editable}
      onSave={saveLexeme} onClose={() => setEditingLexeme(null)} />}
    {deletingLexeme && <ConfirmationDialog message={`Dzēst leksēmu “${deletingLexeme.lemma}” no melnraksta?`}
      busy={saving} error={error} onConfirm={confirmLexemeDeletion} onCancel={() => setDeletingLexeme(null)} />}
  </details>
}
