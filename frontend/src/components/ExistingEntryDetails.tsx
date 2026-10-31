import { useEffect, useState, type ReactNode } from 'react'
import { getTezaursEntry } from '../api'
import type { TezaursEntryDetails } from '../types'
import { EntryHeading } from './EntryHeading'
import { EntryActions, LexemeActions, SenseActions } from './TezaursActions'

type Props = {
  entryId: number
  children?: ReactNode
  onAddSense?: () => void
}

type Sense = TezaursEntryDetails['senses'][number]
type Lexeme = TezaursEntryDetails['lexemes'][number]

const dataLabels: Record<string, string> = {
  AltLemmas: 'Alternatīvās lemmas',
  Flags: 'Pazīmes',
  FlagText: 'Pazīmju teksts',
  FreeText: 'Pazīmju teksts',
  Frequency: 'Biežums',
  ImportNotices: 'Importa piezīmes',
  LanguageMaterial: 'Valodas materiāls',
  Leftovers: 'Pārpalikumi',
  Pronunciations: 'Izrunas varianti',
  Restriction: 'Ierobežojums',
  StructuralRestrictions: 'Strukturālie ierobežojumi',
  Value: 'Vērtība',
  Etymology: 'Cilme',
  Normative: 'Normatīvais komentārs',
  SciName: 'Zinātniskais nosaukums',
  Source: 'Avots',
}

function label(name: string) {
  return dataLabels[name] ?? name
}

function objectValue(value: unknown) {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null
}

function lower(value: unknown) {
  return String(value).toLocaleLowerCase('lv-LV')
}

function formatFlags(value: unknown) {
  const flags = objectValue(value)
  if (!flags) return ''

  return Object.entries(flags).flatMap(([name, flagValue]) => {
    if (Array.isArray(flagValue)) return flagValue.map(lower)
    if (flagValue === null || flagValue === true || flagValue === 'Jā') return [lower(name)]
    return flagValue === false || flagValue === 'Nē' ? [] : [lower(flagValue)]
  }).join(', ')
}

function restrictionName(value: unknown) {
  const name = lower(value)
  if (name === 'formā/atvasinājumā') return 'formā'
  if (name === 'teikumos / noteikta veida struktūrās') return 'konstrukcijā'
  if (name === 'vispārīgais lietojuma biežums') return 'lieto'
  if (name === 'vārddarināšana (sastāvdaļa)') return 'darinot no'
  if (name === 'vārddarināšana (rezultāts)') return 'darinot vārdu'
  return name
}

function formatRestriction(value: unknown): string {
  const restriction = objectValue(value)
  if (!restriction) return ''

  if (Array.isArray(restriction.AND)) {
    return restriction.AND.map(formatRestriction).filter(Boolean).join(' un ')
  }
  if (Array.isArray(restriction.OR)) {
    return restriction.OR.map(formatRestriction).filter(Boolean).join(' vai ')
  }
  if (restriction.NOT) {
    const negated = formatRestriction(restriction.NOT)
    return negated ? `ne ${negated}` : ''
  }

  const restrictionValue = objectValue(restriction.Value)
  const details = [
    formatFlags(restrictionValue?.Flags),
    restrictionValue?.LanguageMaterial ? `“${restrictionValue.LanguageMaterial}”` : '',
  ].filter(Boolean).join(', ')
  const name = restriction.Restriction ? restrictionName(restriction.Restriction) : ''

  if (restrictionValue) {
    return [
      restriction.Frequency ? lower(restriction.Frequency) : '',
      name ? `${name}:` : '',
      details,
    ].filter(Boolean).join(' ')
  }

  return [
    name ? `${name}:` : '',
    restriction.Frequency ? lower(restriction.Frequency) : '',
  ].filter(Boolean).join(' ')
}

function formatRestrictions(value: unknown) {
  const restrictions = Array.isArray(value) ? value : [value]
  return restrictions.map(formatRestriction).filter(Boolean).join('; ')
}

function grammarData(data: Record<string, unknown> | null) {
  return objectValue(data?.Gram)
}

function formatLexemeGrammar(lexeme: Lexeme) {
  const ownFlags = objectValue(grammarData(lexeme.data)?.Flags) ?? {}
  const flags = { ...lexeme.paradigmData, ...ownFlags }
  const partOfSpeech = typeof flags['Vārdšķira'] === 'string' ? flags['Vārdšķira'] : ''
  const conjugation = typeof flags['Konjugācija'] === 'string' ? flags['Konjugācija'] : ''
  const gender = typeof flags['Dzimte'] === 'string' ? flags['Dzimte'] : ''
  const declension = typeof flags['Deklinācija'] === 'string' ? flags['Deklinācija'] : ''
  const reflexive = flags['Atgriezeniskums'] === 'Jā'

  let paradigm = lower(partOfSpeech)
  if (partOfSpeech === 'Darbības vārds' && conjugation) {
    const kind = conjugation === 'Nekārtns'
      ? 'nekārtns darbības vārds'
      : `${conjugation}. konjugācijas darbības vārds`
    paradigm = reflexive ? `atgriezenisks ${kind}` : kind
  } else if (partOfSpeech === 'Lietvārds') {
    paradigm = [gender ? `${lower(gender)} dzimtes` : '',
      declension ? `${declension}. deklinācijas` : '', 'lietvārds'].filter(Boolean).join(' ')
  }

  const paradigmFields = new Set([
    'Atgriezeniskums', 'Darbības vārda tips', 'Deklinācija', 'Dzimte', 'Konjugācija',
    'Morfotabulas tips', 'Noliegums', 'Pakāpe', 'Prievārdiskais apstākļa vārds', 'Vārdšķira',
  ])
  const remainingFlags = Object.fromEntries(
    Object.entries(flags).filter(([name]) => !paradigmFields.has(name)))
  const properties = formatFlags(remainingFlags)

  return [paradigm, properties].filter(Boolean).join('; ')
}

function lexemeTechnicalInfo(lexeme: Lexeme) {
  let paradigmHeading = lexeme.paradigmCode ?? ''
  if (lexeme.paradigmLegacyNo !== null) {
    paradigmHeading += ` (${lexeme.paradigmLegacyNo})`
  }
  if (lexeme.paradigm) {
    paradigmHeading += ` ${lexeme.paradigm}`
  }

  const preferredOrder = [
    'Noliegums', 'Konjugācija', 'Vārdšķira', 'Atgriezeniskums',
    'Morfotabulas tips', 'Darbības vārda tips',
  ]
  const paradigmFields = lexeme.paradigmData
    ? Object.entries(lexeme.paradigmData).sort(([left], [right]) => {
        const leftIndex = preferredOrder.indexOf(left)
        const rightIndex = preferredOrder.indexOf(right)
        if (leftIndex === -1 && rightIndex === -1) return left.localeCompare(right, 'lv')
        if (leftIndex === -1) return 1
        if (rightIndex === -1) return -1
        return leftIndex - rightIndex
      }).map(([name, value]) => `${name}: ${Array.isArray(value) ? value.join(', ') : String(value)}`)
    : []

  return [
    paradigmHeading,
    lexeme.stem1 ? `Pamatformas celms: ${lexeme.stem1}` : '',
    lexeme.stem2 ? `Tagadnes celms: ${lexeme.stem2}` : '',
    lexeme.stem3 ? `Pagātnes celms: ${lexeme.stem3}` : '',
    ...paradigmFields,
  ].filter(Boolean).join('\n')
}

function UsageFrequency({ extras }: { extras: Record<string, unknown> | null }) {
  const ratio = Number(extras?.ratioPerMille)
  if (!Number.isFinite(ratio)) return null

  let level = 1
  if (ratio >= 0.1) level = 5
  else if (ratio >= 0.01) level = 4
  else if (ratio >= 0.001) level = 3
  else if (ratio >= 0.0001) level = 2

  const perMillion = Math.floor(ratio * 1000)
  const title = ratio >= 0.001
    ? `${perMillion} reizes uz miljonu vārdlietojumu Korpuss.lv`
    : ratio >= 0.0001
      ? '0,1–1 reize uz miljonu vārdlietojumu Korpuss.lv'
      : 'Korpuss.lv sastopams retāk nekā 1 reizi uz 10 miljoniem vārdlietojumu'

  return (
    <div className="usage-frequency" title={title}>
      Lietojuma biežums:
      {[1, 2, 3, 4, 5].map((bar) => (
        <span className={bar <= level ? 'active' : ''} key={bar}>━</span>
      ))}
    </div>
  )
}

function DataValue({ value }: { value: unknown }) {
  if (Array.isArray(value)) return <>{value.map(String).join(' | ')}</>
  if (value && typeof value === 'object') {
    return (
      <ul>
        {Object.entries(value).map(([name, item]) => (
          <li key={name}>
            <strong>{label(name)}:</strong> <DataValue value={item} />
          </li>
        ))}
      </ul>
    )
  }
  return <>{value === null || value === undefined ? '–' : String(value)}</>
}

function ExtraData({
  data,
  grammarAlreadyShown = false,
}: {
  data: Record<string, unknown> | null
  grammarAlreadyShown?: boolean
}) {
  if (!data || Object.keys(data).length === 0) return null

  const gram = grammarData(data)
  const flags = formatFlags(gram?.Flags)
  const restrictions = formatRestrictions(gram?.StructuralRestrictions)
  const otherGramData = gram
    ? Object.fromEntries(Object.entries(gram).filter(
        ([name]) => name !== 'Flags' && name !== 'StructuralRestrictions'))
    : null
  const otherData = Object.fromEntries(Object.entries(data).filter(([name, value]) =>
    name !== 'Gram' && value !== null && value !== '' && (!Array.isArray(value) || value.length > 0)))
  const hasOtherGramData = otherGramData && Object.keys(otherGramData).length > 0
  const hasOtherData = Object.keys(otherData).length > 0

  if ((!flags || grammarAlreadyShown)
    && (!restrictions || grammarAlreadyShown)
    && !hasOtherGramData
    && !hasOtherData) return null

  return (
    <div className="existing-data">
      {!grammarAlreadyShown && flags && <p><strong>Pazīmes:</strong> {flags}</p>}
      {!grammarAlreadyShown && restrictions && (
        <p><strong>Ierobežojumi:</strong> {restrictions}</p>
      )}
      {hasOtherGramData && <DataValue value={otherGramData} />}
      {hasOtherData && <DataValue value={otherData} />}
    </div>
  )
}

export function SenseDetails({ number, sense, actions, isNew = false }: {
  number: string
  sense: Sense
  actions?: ReactNode
  isNew?: boolean
}) {
  const gram = grammarData(sense.data)
  const grammar = [formatFlags(gram?.Flags), formatRestrictions(gram?.StructuralRestrictions)]
    .filter(Boolean).join('; ')
  const gloss = sense.gloss?.trim()
  const displayedGloss = gloss
    ? `${gloss}${/[.!?…]$/.test(gloss) ? '' : '.'}`
    : 'Skaidrojums nav norādīts.'

  return (
    <article className="existing-sense">
      <p>
        {number && <><strong>{number}.</strong>{' '}</>}
        {actions ?? <SenseActions subsense={number.includes('.')} />}
        {grammar && <><span className="sense-metadata">{grammar}</span>{' '}</>}
        {displayedGloss}
        {isNew && <small className="draft-label">Jauna</small>}
        {sense.hidden && ' · paslēpta'}
      </p>
      {sense.examples.length > 0 && (
        <ul>
          {sense.examples.map((example) => <li key={example.id}>{example.content}</li>)}
        </ul>
      )}
      <ExtraData data={sense.data} grammarAlreadyShown />
    </article>
  )
}

export function ExistingEntryDetails({ entryId, children, onAddSense }: Props) {
  const [entry, setEntry] = useState<TezaursEntryDetails | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    getTezaursEntry(entryId)
      .then((loaded) => {
        if (active) setEntry(loaded)
      })
      .catch((cause) => {
        if (active) {
          setError(cause instanceof Error ? cause.message : 'Neizdevās ielādēt šķirkli.')
        }
      })
    return () => {
      active = false
    }
  }, [entryId])

  if (error) return <p className="error">{error}</p>
  if (!entry) return <p className="muted">Ielādē esošo šķirkli...</p>

  return (
    <details open className="existing-entry">
      <summary><strong>3. Esošais šķirklis</strong></summary>
      <EntryDisplay entry={entry} onAddSense={onAddSense}>{children}</EntryDisplay>
    </details>
  )
}

type DisplayProps = {
  entry: TezaursEntryDetails
  children?: ReactNode
  onEditEntry?: () => void
  onEditLexeme?: (index: number) => void
  onAddLexeme?: () => void
  onAddSense?: () => void
  onDeleteLexeme?: (index: number) => void
  canDeleteLexeme?: (index: number) => boolean
  publicHumanKey?: string
  lexemeControls?: ReactNode
}

export function EntryDisplay({ entry, children, onEditEntry, onEditLexeme, onAddLexeme, onAddSense,
  onDeleteLexeme, canDeleteLexeme, publicHumanKey, lexemeControls }: DisplayProps) {
  const mainSenses = entry.senses.filter((sense) => sense.parentSenseId === null)
  const gram = grammarData(entry.data)
  const entryGrammar = [formatFlags(gram?.Flags), formatRestrictions(gram?.StructuralRestrictions)]
    .filter(Boolean).join('; ')
  return (
    <div className="tezaurs-entry-display">
      <div className="draft-heading">
        <EntryActions onEdit={onEditEntry} onAddLexeme={onAddLexeme} onAddSense={onAddSense}
          publicHumanKey={publicHumanKey ?? (entry.id > 0 ? entry.humanKey : undefined)} />
        <EntryHeading heading={entry.heading} humanKey={entry.humanKey} />
        {entryGrammar && <span className="lexeme-metadata">{entryGrammar}</span>}
        {entry.hidden && ' · paslēpts'}
      </div>

      {entry.notes && <p><strong>Piezīmes:</strong> {entry.notes}</p>}
      <ExtraData data={entry.data} grammarAlreadyShown />

      {lexemeControls}
      {entry.lexemes.map((lexeme, index) => {
        const grammar = formatLexemeGrammar(lexeme)
        const technicalInfo = lexemeTechnicalInfo(lexeme)
        const pronunciations = Array.isArray(lexeme.data?.Pronunciations)
          ? lexeme.data.Pronunciations.map(String)
          : []
        return (
          <div className="existing-lexeme" key={lexeme.id}>
            <div>
              <LexemeActions onEdit={onEditLexeme ? () => onEditLexeme(index) : undefined}
                onDelete={onDeleteLexeme && canDeleteLexeme?.(index) ? () => onDeleteLexeme(index) : undefined} />
              <strong title={lexeme.role ?? undefined}>{lexeme.lemma}</strong>{' '}
              {pronunciations.length > 0 && <span>[{pronunciations.join('], [')}] </span>}
              {grammar && <span className="lexeme-metadata">{grammar}</span>}
              {technicalInfo && (
                <span aria-label="Paradigmas informācija" className="lexeme-symbol"
                  title={technicalInfo}>¶</span>
              )}
              {lexeme.hidden && <span> · paslēpta</span>}
            </div>
            <UsageFrequency extras={lexeme.extras} />
          </div>
        )
      })}

      {children || <>
      {mainSenses.map((sense, senseIndex) => {
        const senseNumber = String(senseIndex + 1)
        const subSenses = entry.senses.filter((item) => item.parentSenseId === sense.id)
        return (
          <div key={sense.id}>
            <SenseDetails number={mainSenses.length > 1 || subSenses.length > 0 ? senseNumber : ''} sense={sense} />
            {subSenses.length > 0 && (
              <div className="existing-subsenses">
                {subSenses.map((subSense, subSenseIndex) => (
                  <SenseDetails
                    key={subSense.id}
                    number={`${senseNumber}.${subSenseIndex + 1}`}
                    sense={subSense}
                  />
                ))}
              </div>
            )}
          </div>
        )
      })}
      </>}

      {entry.examples.length > 0 && (
        <>
          <h4>Šķirkļa piemēri</h4>
          <ul>{entry.examples.map((example) => <li key={example.id}>{example.content}</li>)}</ul>
        </>
      )}

      {entry.sources.length > 0 && (
        <>
          <h4>Avoti</h4>
          <ul>
            {entry.sources.map((source) => (
              <li key={source.id}>
                {source.url
                  ? <a href={source.url} rel="noreferrer" target="_blank">{source.abbreviation || source.title}</a>
                  : source.abbreviation || source.title}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}
