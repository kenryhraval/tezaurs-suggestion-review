import type { EntryDraft, TezaursEntryDetails, TezaursFormOptions } from '../api/types'
import { buildDraftGrammarData } from './grammar'

export function buildDraftEntryView(
  entryDraft: EntryDraft,
  formOptions: TezaursFormOptions,
): TezaursEntryDetails {
  return {
    id: 0,
    heading: entryDraft.heading,
    humanKey: entryDraft.humanKey,
    typeId: entryDraft.typeId,
    type: formOptions.entryTypes.find((entryType) => entryType.id === entryDraft.typeId)?.label ?? null,
    hidden: false,
    notes: entryDraft.notes,
    data: {
      ...buildDraftGrammarData(null, entryDraft.flags),
      Etymology: entryDraft.etymology,
      Normative: entryDraft.normativeComment,
      SciName: entryDraft.scientificName,
      Source: entryDraft.source,
    },
    lexemes: entryDraft.lexemes.map((lexeme, lexemeIndex) => {
      const paradigm = formOptions.paradigms.find((paradigmOption) => paradigmOption.id === lexeme.paradigmId)
      return {
        id: lexemeIndex,
        lemma: lexeme.lemma,
        roleId: lexeme.roleId,
        role: formOptions.lexemeRoles.find((lexemeRole) => lexemeRole.id === lexeme.roleId)?.label ?? null,
        paradigmId: lexeme.paradigmId,
        paradigmLegacyNo: paradigm?.legacyNo ?? null,
        paradigmCode: paradigm?.code ?? null,
        paradigm: paradigm?.label ?? null,
        paradigmData: paradigm?.data ?? null,
        stem1: lexeme.stem1,
        stem2: lexeme.stem2,
        stem3: lexeme.stem3,
        hidden: false,
        data: { ...buildDraftGrammarData(null, lexeme.flags, lexeme.structuralRestrictions),
          Pronunciations: lexeme.pronunciations },
        extras: null,
      }
    }),
    senses: [],
    examples: [],
    sources: [],
  }
}
