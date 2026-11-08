import type { FlagMap } from '../api/types'

export function buildDraftGrammarData(
  originalData: Record<string, unknown> | null | undefined,
  draftFlags: FlagMap,
  structuralRestrictions?: Record<string, unknown> | null,
) {
  // Preserve the other possible original data, replacing only flags and supplied restrictions
  // inside Gram so the shared Tēzaurs display can render the draft.
  let grammarData: Record<string, unknown> = {}
  if (originalData?.Gram && typeof originalData.Gram === 'object' && !Array.isArray(originalData.Gram)) {
    grammarData = { ...originalData.Gram }
  }

  grammarData.Flags = draftFlags
  if (structuralRestrictions !== undefined) {
    grammarData.StructuralRestrictions = structuralRestrictions
  }
  return { ...originalData, Gram: grammarData }
}
