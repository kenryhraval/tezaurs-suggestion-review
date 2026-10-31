import { useEffect, useState, type FormEvent } from 'react'
import {
  createEntryDraft,
  createLexemeDraft,
  deleteLexemeDraft,
  getEntryDraft,
  getTezaursFormOptions,
  updateEntryDraft,
  updateLexemeDraft,
} from '../api'
import type {
  EntryDraft,
  FlagMap,
  LexemeDraft,
  Review,
  SaveEntryDraft,
  SaveLexemeDraft,
  TezaursFormOptions,
} from '../types'
import { FlagEditor } from './FlagEditor'
import { RestrictionEditor } from './RestrictionEditor'

type Props = {
  reviewId: string
  reviewedTerm: string
  onReviewUpdated: (review: Review) => void
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

export function EntryDraftForm({ reviewId, reviewedTerm, onReviewUpdated }: Props) {
  const [draft, setDraft] = useState<EntryDraft | null>(null)
  const [options, setOptions] = useState<TezaursFormOptions | null>(null)
  const [lexemes, setLexemes] = useState<LexemeDraft[]>([])
  const [entryFlags, setEntryFlags] = useState<FlagMap>({})
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [savingLexemeIndex, setSavingLexemeIndex] = useState<number | null>(null)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true

    Promise.all([getEntryDraft(reviewId), getTezaursFormOptions()])
      .then(([loadedDraft, loadedOptions]) => {
        if (!active) return
        setOptions(loadedOptions)
        setDraft(loadedDraft)

        if (loadedDraft) {
          setLexemes(loadedDraft.lexemes)
          setEntryFlags(loadedDraft.flags)
        } else {
          const firstRoleId = loadedOptions.lexemeRoles[0]?.id ?? 1
          setLexemes([newLexeme(reviewedTerm, firstRoleId, true)])
        }
      })
      .catch((cause) => {
        if (active) {
          setError(cause instanceof Error ? cause.message : 'Neizdevās ielādēt šķirkļa formu.')
        }
      })
      .finally(() => {
        if (active) setLoading(false)
      })

    return () => {
      active = false
    }
  }, [reviewedTerm, reviewId])

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
      const response = lexeme.id
        ? await updateLexemeDraft(reviewId, lexeme.id, lexemeRequest(lexeme))
        : await createLexemeDraft(reviewId, lexemeRequest(lexeme))
      replaceLexeme(index, response.lexeme)
      onReviewUpdated(response.review)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt leksēmu.')
    } finally {
      setSavingLexemeIndex(null)
    }
  }

  async function selectPrimary(lexemeId: string) {
    const index = lexemes.findIndex((lexeme) => lexeme.id === lexemeId)
    if (index < 0 || lexemes[index].primary) return

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
      setLexemes((current) => current.filter((_, itemIndex) => itemIndex !== index))
      return
    }

    setSavingLexemeIndex(index)
    setError('')
    try {
      const review = await deleteLexemeDraft(reviewId, lexeme.id)
      setLexemes((current) => current.filter((_, itemIndex) => itemIndex !== index))
      onReviewUpdated(review)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās dzēst leksēmu.')
    } finally {
      setSavingLexemeIndex(null)
    }
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
    setSaved(false)
    setError('')
    try {
      const response = draft
        ? await updateEntryDraft(reviewId, request)
        : await createEntryDraft(reviewId, request, lexemeRequest(lexemes[0]))
      setDraft(response.draft)
      if (!draft) {
        setLexemes(response.draft.lexemes)
      }
      onReviewUpdated(response.review)
      setSaved(true)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Neizdevās saglabāt šķirkļa melnrakstu.')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <p className="muted">Ielādē šķirkļa formu...</p>
  if (!options) return <p className="error">{error || 'Šķirkļa forma nav pieejama.'}</p>

  const entryTypeId = draft?.typeId ?? options.entryTypes[0]?.id ?? 1
  const formId = `entry-draft-${reviewId}`
  const savedLexemes = lexemes.filter((lexeme) => lexeme.id !== null)
  const primaryLexemeId = savedLexemes.find((lexeme) => lexeme.primary)?.id ?? ''

  return (
    <section className="entry-draft-section">
      <h3>Jauna šķirkļa melnraksts</h3>
      <p className="muted">Dati tiek saglabāti tikai izskatīšanas vidē.</p>

      <form
        id={formId}
        key={draft?.id ?? reviewId}
        className="entry-draft-form"
        onSubmit={submit}
      >
        <fieldset>
          <legend>{draft?.humanKey ?? reviewedTerm}</legend>
          <label>
            Šķirkļa vārds
            <input defaultValue={draft?.heading ?? reviewedTerm} maxLength={255} name="heading" required />
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

        {draft && (
          <button disabled={saving} type="submit">
            {saving ? 'Saglabā...' : 'Saglabāt šķirkli'}
          </button>
        )}
      </form>

      {savedLexemes.length > 1 && (
        <label>
          Galvenā leksēma
          <select
            disabled={savingLexemeIndex !== null}
            onChange={(event) => void selectPrimary(event.target.value)}
            value={primaryLexemeId}
          >
            {savedLexemes.map((lexeme) => (
              <option key={lexeme.id} value={lexeme.id ?? ''}>{lexeme.lemma}</option>
            ))}
          </select>
        </label>
      )}

      <fieldset>
        <legend>Leksēmas</legend>
        {lexemes.map((lexeme, index) => {
          const selectedParadigm = options.paradigms.find(
            (option) => option.id === lexeme.paradigmId,
          )
          return (
            <div className="lexeme-draft" key={lexeme.id ?? index}>
              <label>
                Pamatforma
                <input
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
                      .filter(Boolean),
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
              {draft && (
                <>
                  <button
                    disabled={savingLexemeIndex !== null}
                    type="button"
                    onClick={() => saveLexeme(index)}
                  >
                    {savingLexemeIndex === index ? 'Saglabā...' : 'Saglabāt leksēmu'}
                  </button>
                  {lexemes.length > 1 && !lexeme.primary && (
                    <button
                      disabled={savingLexemeIndex !== null}
                      type="button"
                      onClick={() => removeLexeme(index)}
                    >
                      Noņemt leksēmu
                    </button>
                  )}
                </>
              )}
            </div>
          )
        })}
        {draft ? (
          <button
            type="button"
            onClick={() => setLexemes((current) => [
              ...current,
              newLexeme('', options.lexemeRoles[0]?.id ?? 1, false),
            ])}
          >
            Pievienot leksēmu
          </button>
        ) : (
          <button disabled={saving || lexemes.length === 0} form={formId} type="submit">
            {saving ? 'Saglabā...' : 'Saglabāt šķirkli un pirmo leksēmu'}
          </button>
        )}
      </fieldset>

      {error && <p className="error">{error}</p>}
      {saved && <p>Šķirkļa melnraksts saglabāts.</p>}
    </section>
  )
}
