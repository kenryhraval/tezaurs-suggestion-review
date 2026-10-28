import { useEffect, useState } from 'react'
import { getTezaursEntry } from '../api'
import type { TezaursEntryDetails } from '../types'

type Props = {
  entryId: number
}

function DataValue({ value }: { value: unknown }) {
  if (Array.isArray(value)) return <>{value.map(String).join(' | ')}</>
  if (value && typeof value === 'object') {
    return (
      <dl>
        {Object.entries(value).map(([name, item]) => (
          <div key={name}><dt>{name}</dt><dd><DataValue value={item} /></dd></div>
        ))}
      </dl>
    )
  }
  return <>{value === null || value === undefined ? '–' : String(value)}</>
}

function ExtraData({ data }: { data: Record<string, unknown> | null }) {
  if (!data || Object.keys(data).length === 0) return null
  return (
    <details>
      <summary>Pazīmes un citi dati</summary>
      <DataValue value={data} />
    </details>
  )
}

export function ExistingEntryDetails({ entryId }: Props) {
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
    <section className="existing-entry">
      <h3>Esošais šķirklis</h3>
      <p>
        <strong>{entry.heading}</strong> <small>{entry.humanKey}</small>
        {entry.hidden && ' · paslēpts'}
      </p>

      <dl>
        <dt>Tips</dt><dd>{entry.type ?? entry.typeId}</dd>
        {entry.notes && <><dt>Piezīmes</dt><dd>{entry.notes}</dd></>}
      </dl>
      <ExtraData data={entry.data} />

      <h4>Leksēmas</h4>
      {entry.lexemes.map((lexeme) => (
        <details key={lexeme.id} open={entry.lexemes.length === 1}>
          <summary>{lexeme.lemma}{lexeme.hidden && ' · paslēpta'}</summary>
          <dl>
            <dt>Loma</dt><dd>{lexeme.role ?? lexeme.roleId}</dd>
            <dt>Paradigma</dt><dd>{lexeme.paradigm ?? 'Nav norādīta'}</dd>
            {lexeme.stem1 && <><dt>Pamatformas celms</dt><dd>{lexeme.stem1}</dd></>}
            {lexeme.stem2 && <><dt>Tagadnes celms</dt><dd>{lexeme.stem2}</dd></>}
            {lexeme.stem3 && <><dt>Pagātnes celms</dt><dd>{lexeme.stem3}</dd></>}
          </dl>
          <ExtraData data={lexeme.data} />
        </details>
      ))}

      <h4>Nozīmes</h4>
      {entry.senses.map((sense) => (
        <article className="existing-sense" key={sense.id}>
          <p>
            <strong>{sense.parentSenseId ? 'Apakšnozīme' : sense.orderNo ?? '–'}.</strong>{' '}
            {sense.gloss || 'Skaidrojums nav norādīts.'}
            {sense.hidden && ' · paslēpta'}
          </p>
          {sense.senseTag && <small>{sense.senseTag}</small>}
          {sense.examples.length > 0 && (
            <ul>
              {sense.examples.map((example) => <li key={example.id}>{example.content}</li>)}
            </ul>
          )}
          <ExtraData data={sense.data} />
        </article>
      ))}

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
    </section>
  )
}
