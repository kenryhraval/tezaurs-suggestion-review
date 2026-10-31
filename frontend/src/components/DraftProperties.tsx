import type { FlagMap, TezaursFormOptions } from '../types'

type Props = {
  flags: FlagMap
  definitions: TezaursFormOptions['flags']
  restriction?: Record<string, unknown> | null
}

export function DraftProperties({ flags, definitions, restriction }: Props) {
  const restrictionValue = restriction?.Value as {
    Flags?: FlagMap
    LanguageMaterial?: string[]
  } | undefined

  return (
    <>
      {Object.entries(flags).map(([name, value]) => (
        <p key={name}>
          <strong>{definitions.find((flag) => flag.name === name)?.label ?? name}:</strong>{' '}
          {Array.isArray(value) ? value.join(', ') : value ?? 'Jā'}
        </p>
      ))}
      {restriction && (
        <div className="muted">
          <p>Ierobežojums: {String(restriction.Frequency ?? '')}{' '}
            {String(restriction.Restriction ?? 'sarežģīts ierobežojums')}</p>
          {restrictionValue?.Flags && (
            <DraftProperties flags={restrictionValue.Flags} definitions={definitions} />
          )}
          {restrictionValue?.LanguageMaterial && (
            <p>{restrictionValue.LanguageMaterial.join(', ')}</p>
          )}
        </div>
      )}
    </>
  )
}
