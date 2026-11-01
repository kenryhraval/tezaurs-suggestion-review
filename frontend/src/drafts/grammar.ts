import type { FlagMap } from '../api/types'

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
  if (restriction !== undefined) {
    gram.StructuralRestrictions = restriction
  }
  return { ...source, Gram: gram }
}
