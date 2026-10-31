import type { EntryDraft, FlagMap, TezaursEntryDetails, TezaursFormOptions } from './types'

export function draftGrammarData(
  source: Record<string, unknown> | null | undefined,
  flags: FlagMap,
  restriction?: Record<string, unknown> | null,
) {
  let gram: Record<string, unknown> = {}
  if (source?.Gram && typeof source.Gram === 'object' && !Array.isArray(source.Gram)) {
    gram = { ...source.Gram }
  }
  gram.Flags = flags
  if (restriction !== undefined) gram.StructuralRestrictions = restriction
  return { ...source, Gram: gram }
}

export function draftEntryView(
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
