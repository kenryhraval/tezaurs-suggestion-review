import { Fragment, useEffect, useState, type FormEvent, type ReactNode } from 'react'
import {
  createEntryDraft,
  createLexemeDraft,
  deleteLexemeDraft,
  getEntryDraft,
  getTezaursFormOptions,
  updateEntryDraft,
  updateLexemeDraft,
} from '../api/client'
import type {
  EntryDraft,
  FlagMap,
  LexemeDraft,
  Review,
  SaveEntryDraft,
  SaveLexemeDraft,
  TezaursFormOptions,
  TezaursEntryDetails,
} from '../api/types'
import { FlagEditor } from './FlagEditor'
import { RestrictionEditor } from './RestrictionEditor'
import { EditDialog } from './EditDialog'
import { EntryDisplay } from './ExistingEntryDetails'
import { draftGrammarData } from '../drafts/grammar'
import { ConfirmationDialog } from './ConfirmationDialog'

type Props = {
  reviewId: string
  onReviewUpdated: (review: Review) => void
  children?: ReactNode
  defaultOpen: boolean
  footer?: ReactNode
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

function draftEntryView(
  draft: EntryDraft,
  options: TezaursFormOptions,
): TezaursEntryDetails {
  return {
    id: 0,
    heading: draft.heading,
    humanKey: draft.humanKey,
    typeId: draft.typeId,
    type: options.entryTypes.find((type) => type.id === draft.typeId)?.label ?? null,
    hidden: false,
    notes: draft.notes,
    data: {
      ...draftGrammarData(null, draft.flags),
      Etymology: draft.etymology,
      Normative: draft.normativeComment,
      SciName: draft.scientificName,
      Source: draft.source,
    },
    lexemes: draft.lexemes.map((lexeme, index) => {
      const paradigm = options.paradigms.find((item) => item.id === lexeme.paradigmId)
      return {
        id: index,
        lemma: lexeme.lemma,
        roleId: lexeme.roleId,
        role: options.lexemeRoles.find((item) => item.id === lexeme.roleId)?.label ?? null,
        paradigmId: lexeme.paradigmId,
        paradigmLegacyNo: paradigm?.legacyNo ?? null,
        paradigmCode: paradigm?.code ?? null,
        paradigm: paradigm?.label ?? null,
        paradigmData: paradigm?.data ?? null,
        stem1: lexeme.stem1,
        stem2: lexeme.stem2,
        stem3: lexeme.stem3,
        hidden: false,
        data: { ...draftGrammarData(null, lexeme.flags, lexeme.structuralRestrictions),
          Pronunciations: lexeme.pronunciations },
        extras: null,
      }
    }),
    senses: [],
    examples: [],
    sources: [],
  }
}

export function EntryDraftForm({ reviewId, onReviewUpdated, children, footer, editable, onAddSense, defaultOpen }: Props) {
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
    const data = new FormData(event.currentTarget)
    function optionalFormValue(name: string) {
      const formValue = String(data.get(name) ?? '').trim()
      return formValue === '' ? null : formValue
    }
    const request: SaveEntryDraft = {
      heading: String(data.get('heading')),
      typeId: Number(data.get('typeId')),
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
      const response = await updateEntryDraft(reviewId, request)
      setDraft(response.draft)
      onReviewUpdated(response.review)
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

  if (loading) {
    return <p className="muted">Ielādē šķirkļa formu...</p>
  }
  if (!options || !draft) {
    return <p className="error">{error || 'Šķirkļa forma nav pieejama.'}</p>
  }

  const entryTypeId = draft.typeId
  const savedLexemes = lexemes.filter((lexeme) => lexeme.id !== null)
  const primaryLexemeId = savedLexemes.find((lexeme) => lexeme.primary)?.id ?? ''

  return (
    <details open={defaultOpen} className="entry-draft-section existing-entry">
      <summary><strong>4. Jauna šķirkļa melnraksts</strong></summary>

      <EntryDisplay entry={draftEntryView(draft, options)}
        onEditEntry={editable ? openEntryEditor : undefined}
        onEditLexeme={editable ? openLexemeEditor : undefined}
        onAddLexeme={editable ? addLexeme : undefined}
        onAddSense={onAddSense}
        onDeleteLexeme={editable ? requestLexemeDeletion : undefined}
        canDeleteLexeme={(index) => draft.lexemes.length > 1 && !draft.lexemes[index].primary}
        lexemeControls={editable && savedLexemes.length > 1 && (
          <label>Galvenā leksēma
            <select value={primaryLexemeId} disabled={savingLexemeIndex !== null}
              onChange={(event) => selectPrimary(event.target.value)}>
              {savedLexemes.map((lexeme) => <option key={lexeme.id} value={lexeme.id ?? ''}>{lexeme.lemma}</option>)}
            </select>
          </label>
        )}>
        {children}
      </EntryDisplay>
      {footer}

      {editingEntry && (
        <EditDialog title="Labot šķirkli" busy={saving} error={error}
          onSubmit={submit} onCancel={cancelEntryEdit}>
          <fieldset>
            <legend>Šķirklis</legend>
            <label>
              Šķirkļa vārds
              <input autoFocus defaultValue={draft.heading} maxLength={255} name="heading" required />
            </label>
            <label>
              Šķirkļa tips
              <select defaultValue={entryTypeId} name="typeId">
                {options.entryTypes.map((option) => (
                  <option key={option.id} value={option.id}>{option.label || option.code}</option>
                ))}
              </select>
            </label>
            <FlagEditor definitions={options.flags} flags={entryFlags} onChange={setEntryFlags} scope="E" />
          </fieldset>

          <fieldset>
            <legend>Papildu informācija</legend>
            <label>Cilme<textarea defaultValue={draft?.etymology ?? ''} name="etymology" rows={2} /></label>
            <label>Normatīvais komentārs<textarea defaultValue={draft?.normativeComment ?? ''} name="normativeComment" rows={2} /></label>
            <label>Zinātniskais nosaukums<input defaultValue={draft?.scientificName ?? ''} name="scientificName" /></label>
            <label>Avots<input defaultValue={draft?.source ?? ''} name="source" /></label>
            <label>Iekšējās piezīmes<textarea defaultValue={draft?.notes ?? ''} name="notes" rows={3} /></label>
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
              <EditDialog title={lexeme.id ? 'Labot leksēmu' : 'Pievienot leksēmu'}
                busy={savingLexemeIndex !== null} error={error}
                onCancel={() => cancelLexemeEdit(index)} onSubmit={() => {
                  saveLexeme(index)
                }}>
                <label>
                  Pamatforma
                  <input
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
                  <select
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
                  <select
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
                      <input
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
                        <input
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
                        <input
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
                  <input
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
    </details>
  )
}
