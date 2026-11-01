import type { FlagMap, TezaursFlagDefinition, TezaursOption, RestrictionFrequency } from '../api/types'
import { FlagEditor } from './FlagEditor'

type Props = {
  definitions: TezaursFlagDefinition[]
  frequencies: RestrictionFrequency[]
  onChange: (restriction: Record<string, unknown> | null) => void
  restriction: Record<string, unknown> | null
  types: TezaursOption[]
}

function readObject(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return {}
  }
  return value as Record<string, unknown>
}

export function RestrictionEditor({
  definitions,
  frequencies,
  onChange,
  restriction,
  types,
}: Props) {
  if (!restriction) {
    return (
      <button type="button" onClick={() => onChange({})}>
        Pievienot ierobežojumu
      </button>
    )
  }

  if (restriction.AND || restriction.OR || restriction.NOT) {
    return (
      <div className="restriction-editor">
        <p>Sarežģītu ierobežojumu šī forma vēl neatbalsta.</p>
        <button type="button" onClick={() => onChange(null)}>Noņemt ierobežojumu</button>
      </div>
    )
  }

  if (!Array.isArray(types) || !Array.isArray(frequencies) || !Array.isArray(definitions)) {
    return (
      <div className="restriction-editor">
        <p className="error">Ierobežojumu izvēles nav ielādētas. Pārstartējiet backend.</p>
        <button type="button" onClick={() => onChange(null)}>Noņemt ierobežojumu</button>
      </div>
    )
  }

  const restrictionType = typeof restriction.Restriction === 'string' ? restriction.Restriction : ''
  const frequency = typeof restriction.Frequency === 'string' ? restriction.Frequency : ''
  const restrictionValue = readObject(restriction.Value)
  const flags = readObject(restrictionValue.Flags) as FlagMap
  const languageMaterial = Array.isArray(restrictionValue.LanguageMaterial)
    ? restrictionValue.LanguageMaterial.map(String)
    : []
  const isOverallFrequency = restrictionType === 'Vispārīgais lietojuma biežums'

  function updateType(nextType: string) {
    const updated: Record<string, unknown> = {
      ...restriction,
      Restriction: nextType || null,
    }
    if (nextType === 'Vispārīgais lietojuma biežums') {
      delete updated.Value
    }
    onChange(updated)
  }

  function updateFlags(updatedFlags: FlagMap) {
    onChange({
      ...restriction,
      Value: { ...restrictionValue, Flags: updatedFlags },
    })
  }

  function updateLanguageMaterial(text: string) {
    const items = text.split('|').map((item) => item.trim()).filter((value) => value !== '')
    onChange({
      ...restriction,
      Value: { ...restrictionValue, LanguageMaterial: items.length > 0 ? items : null },
    })
  }

  return (
    <div className="restriction-editor">
      <strong>Ierobežojums</strong>
      <label>
        Veids
        <select value={restrictionType} onChange={(event) => updateType(event.target.value)}>
          <option value="">Izvēlieties</option>
          {types.map((option) => (
            <option key={option.id} value={option.label}>{option.label}</option>
          ))}
        </select>
      </label>
      <label>
        Biežums
        <select
          value={frequency}
          onChange={(event) => onChange({
            ...restriction,
            Frequency: event.target.value || null,
          })}
        >
          <option value="">Nav norādīts</option>
          {frequencies.filter((option) => option.label).map((option) => (
            <option key={option.id} value={option.label ?? ''}>{option.label}</option>
          ))}
        </select>
      </label>
      {!isOverallFrequency && (
        <>
          <FlagEditor
            definitions={definitions}
            flags={flags}
            onChange={updateFlags}
            scope="R"
          />
          <label>
            Valodas materiāls
            <input
              onChange={(event) => updateLanguageMaterial(event.target.value)}
              value={languageMaterial.join(' | ')}
            />
          </label>
        </>
      )}
      <button type="button" onClick={() => onChange(null)}>Noņemt ierobežojumu</button>
    </div>
  )
}
