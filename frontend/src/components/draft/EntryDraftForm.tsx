import { Fragment, useEffect, useState, type FormEvent, type ReactNode } from 'react'
import {
  createEntryDraft,
  createLexemeDraft,
  deleteLexemeDraft,
  getEntryDraft,
  getTezaursFormOptions,
  updateEntryDraft,
  updateLexemeDraft,
} from '../../api/client'
import type {
  EntryDraft,
  FlagMap,
  LexemeDraft,
  Review,
  SaveEntryDraft,
  SaveLexemeDraft,
  TezaursFormOptions,
} from '../../api/types'
import { FlagEditor } from './FlagEditor'
import { RestrictionEditor } from './RestrictionEditor'
import { EditDialog } from '../shared/EditDialog'
import { EntryHeading } from '../tezaurs/EntryHeading'
import { EntryActions, LexemeActions } from '../tezaurs/TezaursActions'
import { ConfirmationDialog } from '../shared/ConfirmationDialog'

type Props = {
  reviewId: string
  onReviewUpdated: (review: Review) => void
  children?: ReactNode
  defaultOpen: boolean
  canCollapse: boolean
  complete: boolean
  editable: boolean
  onAddSense?: () => void
}

function newLexeme(term: string, roleId: number, primary: boolean): LexemeDraft {
  return {
    id: null,
    lemma: term,
    primary,
    roleId,
    paradigmId: null,
    stem1: null,
    stem2: null,
    stem3: null,
    pronunciations: [],
    flags: {},
    structuralRestrictions: null,
  }
}

function lexemeRequest(lexeme: LexemeDraft): SaveLexemeDraft {
  return {
    lemma: lexeme.lemma,
    primary: lexeme.primary,
    roleId: lexeme.roleId,
    paradigmId: lexeme.paradigmId,
    stem1: lexeme.stem1,
    stem2: lexeme.stem2,
    stem3: lexeme.stem3,
    pronunciations: lexeme.pronunciations,
    flags: lexeme.flags,
    structuralRestrictions: lexeme.structuralRestrictions,
  }
}

export function EntryDraftForm({ reviewId, onReviewUpdated, children, editable, onAddSense, defaultOpen, canCollapse, complete }: Props) {
  const [expanded, setExpanded] = useState(defaultOpen)
  const [draft, setDraft] = useState<EntryDraft | null>(null)
  const [options, setOptions] = useState<TezaursFormOptions | null>(null)
  const [lexemes, setLexemes] = useState<LexemeDraft[]>([])
  const [entryFlags, setEntryFlags] = useState<FlagMap>({})
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [savingLexemeIndex, setSavingLexemeIndex] = useState<number | null>(null)
  const [editingEntry, setEditingEntry] = useState(false)
  const [editingLexemeIndex, setEditingLexemeIndex] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [deletingLexemeIndex, setDeletingLexemeIndex] = useState<number | null>(null)

  useEffect(() => {
    let requestIsOutdated = false

    async function loadDraftForm() {
      try {
        const [savedDraft, loadedOptions] = await Promise.all([
          getEntryDraft(reviewId),
          getTezaursFormOptions(),
        ])
        if (requestIsOutdated) {
          return
        }

        let loadedDraft = savedDraft
        if (!loadedDraft && editable) {
          const response = await createEntryDraft(reviewId)
          loadedDraft = response.draft
        }
        if (!loadedDraft) {
          throw new Error('Šķirkļa melnraksts nav pieejams.')
        }
        if (requestIsOutdated) {
          return
        }

        setOptions(loadedOptions)
        setDraft(loadedDraft)
        setLexemes(loadedDraft.lexemes)
        setEntryFlags(loadedDraft.flags)
      } catch (cause) {
        if (!requestIsOutdated) {
          setError(cause instanceof Error ? cause.message : 'Neizdevās ielādēt šķirkļa formu.')
        }
      } finally {
        if (!requestIsOutdated) {
          setLoading(false)
        }
      }
    }

    loadDraftForm()

    return () => {
      requestIsOutdated = true
    }
  }, [reviewId, editable])

  function replaceLexeme(index: number, updatedLexeme: LexemeDraft) {
    setLexemes((currentLexemes) => {
      const updatedLexemes = [...currentLexemes]
      updatedLexemes[index] = updatedLexeme
      return updatedLexemes
    })
  }

  async function saveLexeme(index: number) {
    if (!editable) return
    const lexeme = lexemes[index]
    setSavingLexemeIndex(index)
    setError('')
    try {
      const request = lexemeRequest(lexeme)
      let response
      if (lexeme.id) {
        response = await updateLexemeDraft(reviewId, lexeme.id, request)
      } else {
        response = await createLexemeDraft(reviewId, request)
      }
      replaceLexeme(index, response.lexeme)
      setDraft((currentDraft) => {
        if (!currentDraft) {
          return currentDraft
        }
        const savedLexemes = lexemes.map((item, itemIndex) => {
          return itemIndex === index ? response.lexeme : item
        })
        return { ...currentDraft, lexemes: savedLexemes }
      })
      setEditingLexemeIndex(null)
      onReviewUpdated(response.review)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt leksēmu.')
    } finally {
      setSavingLexemeIndex(null)
    }
  }

  async function selectPrimary(lexemeId: string) {
    if (!editable) return
    const index = lexemes.findIndex((lexeme) => lexeme.id === lexemeId)
    if (index < 0 || lexemes[index].primary) {
      return
    }

    const previousLexemes = lexemes
    const selectedLexeme = { ...lexemes[index], primary: true }
    setLexemes(lexemes.map((lexeme, itemIndex) => ({
      ...lexeme,
      primary: itemIndex === index,
    })))
    setSavingLexemeIndex(index)
    setError('')
    try {
      const response = await updateLexemeDraft(
        reviewId,
        lexemeId,
        lexemeRequest(selectedLexeme),
      )
      replaceLexeme(index, response.lexeme)
      setDraft((currentDraft) => {
        if (!currentDraft) {
          return currentDraft
        }
        const savedLexemes = lexemes.map((item, itemIndex) => ({
          ...item,
          primary: itemIndex === index,
        }))
        return { ...currentDraft, lexemes: savedLexemes }
      })
      onReviewUpdated(response.review)
    } catch (cause) {
      setLexemes(previousLexemes)
      setError(cause instanceof Error ? cause.message : 'Neizdevās mainīt galveno leksēmu.')
    } finally {
      setSavingLexemeIndex(null)
    }
  }

  async function removeLexeme(index: number) {
    if (!editable) return false
    const lexeme = lexemes[index]
    if (!lexeme.id) {
      return false
    }

    setSavingLexemeIndex(index)
    setError('')
    try {
      const review = await deleteLexemeDraft(reviewId, lexeme.id)
      setLexemes((current) => current.filter((_, itemIndex) => itemIndex !== index))
      setDraft((currentDraft) => {
        if (!currentDraft) {
          return currentDraft
        }
        const remainingLexemes = currentDraft.lexemes.filter((item) => item.id !== lexeme.id)
        return { ...currentDraft, lexemes: remainingLexemes }
      })
      setEditingLexemeIndex(null)
      onReviewUpdated(review)
      return true
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās dzēst leksēmu.')
      return false
    } finally {
      setSavingLexemeIndex(null)
    }
  }

  function cancelEntryEdit() {
    setEntryFlags(draft?.flags ?? {})
    setEditingEntry(false)
    setError('')
  }

  function cancelLexemeEdit(index: number) {
    const lexeme = lexemes[index]
    if (lexeme.id) {
      const savedLexeme = draft?.lexemes.find((item) => item.id === lexeme.id)
      if (savedLexeme) {
        replaceLexeme(index, savedLexeme)
      }
    } else {
      setLexemes((current) => current.filter((_, itemIndex) => itemIndex !== index))
    }
    setEditingLexemeIndex(null)
    setError('')
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editable) return
    const formData = new FormData(event.currentTarget)
    function optionalFormValue(name: string) {
      const formValue = String(formData.get(name) ?? '').trim()
      return formValue === '' ? null : formValue
    }
    const entryRequest: SaveEntryDraft = {
      heading: String(formData.get('heading')),
      typeId: Number(formData.get('typeId')),
      etymology: optionalFormValue('etymology'),
      normativeComment: optionalFormValue('normativeComment'),
      scientificName: optionalFormValue('scientificName'),
      source: optionalFormValue('source'),
      notes: optionalFormValue('notes'),
      flags: entryFlags,
    }

    setSaving(true)
    setError('')
    try {
      const savedEntryResponse = await updateEntryDraft(reviewId, entryRequest)
      setDraft(savedEntryResponse.draft)
      onReviewUpdated(savedEntryResponse.review)
      setEditingEntry(false)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt šķirkļa melnrakstu.')
    } finally {
      setSaving(false)
    }
  }

  function openEntryEditor() {
    setError('')
    setEditingEntry(true)
  }

  function openLexemeEditor(index: number) {
    setError('')
    setEditingLexemeIndex(index)
  }

  function addLexeme() {
    if (!editable) return
    if (!options) {
      return
    }
    setError('')
    setEditingLexemeIndex(lexemes.length)
    const defaultRoleId = options.lexemeRoles[0]?.id ?? 1
    const addedLexeme = newLexeme('', defaultRoleId, false)
    setLexemes((currentLexemes) => [...currentLexemes, addedLexeme])
  }

  function requestLexemeDeletion(index: number) {
    setError('')
    setDeletingLexemeIndex(index)
  }

  async function confirmLexemeDeletion() {
    if (deletingLexemeIndex === null) {
      return
    }
    const removed = await removeLexeme(deletingLexemeIndex)
    if (removed) {
      setDeletingLexemeIndex(null)
    }
  }

  function cancelLexemeDeletion() {
    setDeletingLexemeIndex(null)
    setError('')
  }

  function renderSection(content: ReactNode) {
    return (
      <details open={expanded || !canCollapse} onToggle={(event) => setExpanded(event.currentTarget.open)}
        className="entry-draft-section existing-entry review-section">
        <summary className={!expanded && complete ? 'section-title confirmed' : 'section-title'} aria-disabled={!canCollapse} onClick={(event) => {
          if (!canCollapse) event.preventDefault()
        }}>4. Labojumu melnraksts</summary>
        {content}
      </details>
    )
  }

  if (loading) {
    return renderSection(<p className="muted">Ielādē šķirkļa formu...</p>)
  }
  if (!options || !draft) {
    return <p className="error">{error || 'Šķirkļa forma nav pieejama.'}</p>
  }

  const entryTypeId = draft.typeId
  const savedLexemes = lexemes.filter((lexeme) => lexeme.id !== null)
  const primaryLexemeId = savedLexemes.find((lexeme) => lexeme.primary)?.id ?? ''

  return renderSection(
    <>
      <div className="draft-heading">
        <EntryActions onEdit={openEntryEditor}
          onAddLexeme={editable ? addLexeme : undefined} readOnly={!editable} onAddSense={onAddSense} />
        <EntryHeading heading={draft.heading} humanKey={draft.humanKey} />
      </div>
      {savedLexemes.length > 1 && (
        <label>Galvenā leksēma
          <select className="custom-select custom-select-sm" value={primaryLexemeId}
            disabled={!editable || savingLexemeIndex !== null}
            onChange={(event) => selectPrimary(event.target.value)}>
            {savedLexemes.map((lexeme) => <option key={lexeme.id} value={lexeme.id ?? ''}>{lexeme.lemma}</option>)}
          </select>
        </label>
      )}
      {savedLexemes.map((lexeme) => {
        const index = lexemes.indexOf(lexeme)
        return (
          <div className="draft-lexeme" key={lexeme.id}>
            <LexemeActions onEdit={() => openLexemeEditor(index)}
              readOnly={!editable} onDelete={editable && savedLexemes.length > 1 && !lexeme.primary
                ? () => requestLexemeDeletion(index) : undefined} />
            <strong>{lexeme.lemma}</strong>
          </div>
        )
      })}
      {children}

      {editingEntry && (
        <EditDialog title={editable ? "Labot šķirkli" : "Šķirkļa dati"} busy={saving} error={error} readOnly={!editable}
          onSubmit={submit} onCancel={cancelEntryEdit}>
          <fieldset>
            <legend>Šķirklis</legend>
            <label>
              Šķirkļa vārds
              <input className="form-control form-control-sm" autoFocus defaultValue={draft.heading} maxLength={255} name="heading" required />
            </label>
            <label>
              Šķirkļa tips
              <select className="custom-select custom-select-sm" defaultValue={entryTypeId} name="typeId">
                {options.entryTypes.map((option) => (
                  <option key={option.id} value={option.id}>{option.label || option.code}</option>
                ))}
              </select>
            </label>
            <FlagEditor definitions={options.flags} flags={entryFlags} onChange={setEntryFlags} scope="E" />
          </fieldset>

          <fieldset>
            <legend>Papildu informācija</legend>
            <label>Cilme<textarea className="form-control form-control-sm" defaultValue={draft?.etymology ?? ''} name="etymology" rows={2} /></label>
            <label>Normatīvais komentārs<textarea className="form-control form-control-sm" defaultValue={draft?.normativeComment ?? ''} name="normativeComment" rows={2} /></label>
            <label>Zinātniskais nosaukums<input className="form-control form-control-sm" defaultValue={draft?.scientificName ?? ''} name="scientificName" /></label>
            <label>Avots<input className="form-control form-control-sm" defaultValue={draft?.source ?? ''} name="source" /></label>
            <label>Iekšējās piezīmes<textarea className="form-control form-control-sm" defaultValue={draft?.notes ?? ''} name="notes" rows={3} /></label>
          </fieldset>
        </EditDialog>
      )}
      {lexemes.map((lexeme, index) => {
        const selectedParadigm = options.paradigms.find(
          (option) => option.id === lexeme.paradigmId,
        )
        return (
          <Fragment key={lexeme.id ?? index}>
            {editingLexemeIndex === index && (
              <EditDialog title={editable ? (lexeme.id ? 'Labot leksēmu' : 'Pievienot leksēmu') : 'Leksēmas dati'} readOnly={!editable}
                busy={savingLexemeIndex !== null} error={error}
                onCancel={() => cancelLexemeEdit(index)} onSubmit={() => {
                  saveLexeme(index)
                }}>
                <label>
                  Pamatforma
                  <input className="form-control form-control-sm"
                    autoFocus
                    maxLength={255}
                    onChange={(event) => replaceLexeme(index, {
                      ...lexeme,
                      lemma: event.target.value,
                    })}
                    required
                    value={lexeme.lemma}
                  />
                </label>
                <label>
                  Loma šķirklī
                  <select className="custom-select custom-select-sm"
                    onChange={(event) => replaceLexeme(index, {
                      ...lexeme,
                      roleId: Number(event.target.value),
                    })}
                    value={lexeme.roleId}
                  >
                    {options.lexemeRoles.map((option) => (
                      <option key={option.id} value={option.id}>{option.label || option.code}</option>
                    ))}
                  </select>
                </label>
                <label>
                  Paradigma
                  <select className="custom-select custom-select-sm"
                    onChange={(event) => replaceLexeme(index, {
                      ...lexeme,
                      paradigmId: event.target.value ? Number(event.target.value) : null,
                    })}
                    value={lexeme.paradigmId ?? ''}
                  >
                    <option value="">Nav izvēlēta</option>
                    {options.paradigms.map((option) => (
                      <option key={option.id} value={option.id}>{option.code}: {option.label}</option>
                    ))}
                  </select>
                </label>
                {selectedParadigm && (
                  <div className="stem-fields">
                    <label>
                      Pamatformas celms
                      <input className="form-control form-control-sm"
                        onChange={(event) => replaceLexeme(index, {
                          ...lexeme,
                          stem1: event.target.value || null,
                        })}
                        value={lexeme.stem1 ?? ''}
                      />
                    </label>
                    {selectedParadigm.stems >= 2 && (
                      <label>
                        Tagadnes celms
                        <input className="form-control form-control-sm"
                          onChange={(event) => replaceLexeme(index, {
                            ...lexeme,
                            stem2: event.target.value || null,
                          })}
                          value={lexeme.stem2 ?? ''}
                        />
                      </label>
                    )}
                    {selectedParadigm.stems >= 3 && (
                      <label>
                        Pagātnes celms
                        <input className="form-control form-control-sm"
                          onChange={(event) => replaceLexeme(index, {
                            ...lexeme,
                            stem3: event.target.value || null,
                          })}
                          value={lexeme.stem3 ?? ''}
                        />
                      </label>
                    )}
                  </div>
                )}
                <label>
                  Izrunas varianti
                  <input className="form-control form-control-sm"
                    onChange={(event) => replaceLexeme(index, {
                      ...lexeme,
                      pronunciations: event.target.value
                        .split('|')
                        .map((item) => item.trim())
                        .filter((value) => value !== ''),
                    })}
                    value={lexeme.pronunciations.join(' | ')}
                  />
                </label>
                <FlagEditor
                  definitions={options.flags}
                  flags={lexeme.flags}
                  onChange={(flags) => replaceLexeme(index, { ...lexeme, flags })}
                  scope="L"
                />
                <RestrictionEditor
                  definitions={options.flags}
                  frequencies={options.restrictionFrequencies}
                  onChange={(structuralRestrictions) => replaceLexeme(index, {
                    ...lexeme,
                    structuralRestrictions,
                  })}
                  restriction={lexeme.structuralRestrictions}
                  types={options.restrictionTypes}
                />
              </EditDialog>
            )}
          </Fragment>
        )
      })}
      {deletingLexemeIndex !== null && (
        <ConfirmationDialog
          message={`Dzēst leksēmu “${lexemes[deletingLexemeIndex]?.lemma}” no melnraksta?`}
          busy={savingLexemeIndex !== null} error={error}
          onConfirm={confirmLexemeDeletion}
          onCancel={cancelLexemeDeletion}
        />
      )}
      {error && !editingEntry && editingLexemeIndex === null && deletingLexemeIndex === null && <p className="error">{error}</p>}
    </>
  )
}
