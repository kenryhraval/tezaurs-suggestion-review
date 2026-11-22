import type { FlagMap, TezaursFlagDefinition, LexemeDraft, SaveLexemeDraft } from '../../api/types'

export function parseMultipleValues(text: string): string[] {
  return text.split('|').map((value) => value.trim()).filter((value) => value !== '')
}

export function parseFlags(flags: FlagMap, definitions: TezaursFlagDefinition[]): FlagMap {
  const parsed = { ...flags }
  for (const definition of definitions) {
    const value = parsed[definition.name]
    if (definition.multiple && typeof value === 'string') parsed[definition.name] = parseMultipleValues(value)
  }
  return parsed
}

export function lexemeRequest(lexeme: LexemeDraft): SaveLexemeDraft {
  return { lemma: lexeme.lemma, primary: lexeme.primary, roleId: lexeme.roleId, paradigmId: lexeme.paradigmId,
    stem1: lexeme.stem1, stem2: lexeme.stem2, stem3: lexeme.stem3,
    pronunciations: lexeme.pronunciations, flags: lexeme.flags }
}
