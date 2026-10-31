import type { FlagMap, FlagValue, TezaursFormOptions } from '../types'

type Props = {
  scope: 'E' | 'L' | 'R' | 'S'
  definitions: TezaursFormOptions['flags']
  flags: FlagMap
  onChange: (value: FlagMap) => void
}

export function FlagEditor({ scope, definitions, flags, onChange }: Props) {
  const available = definitions.filter((flag) => flag.scopes.includes(scope))
  const selected = available.filter((flag) => flag.name in flags)

  function add(name: string) {
    if (!name) return
    const flag = available.find((item) => item.name === name)
    if (!flag) return
    const initialValue: FlagValue = flag.multiple ? [] : null
    const updatedFlags = { ...flags }
    updatedFlags[name] = initialValue
    onChange(updatedFlags)
  }

  function update(name: string, next: FlagValue) {
    const updatedFlags = { ...flags }
    updatedFlags[name] = next
    onChange(updatedFlags)
  }

  function remove(name: string) {
    const updatedFlags = { ...flags }
    delete updatedFlags[name]
    onChange(updatedFlags)
  }

  return (
    <div className="flag-editor">
      <label>
        Pazīmes
        <select value="" onChange={(event) => add(event.target.value)}>
          <option value="">Pievienot pazīmi</option>
          {available.filter((flag) => !(flag.name in flags)).map((flag) => (
            <option key={flag.name} value={flag.name}>{flag.label}</option>
          ))}
        </select>
      </label>

      {selected.map((flag) => {
        const currentValue = flags[flag.name]
        const selectedValues = Array.isArray(currentValue) ? currentValue : []
        return (
        <div className="flag-row" key={flag.name} title={flag.description ?? undefined}>
          <span>{flag.label}</span>
          {flag.permittedValues === 'E' ? (
            flag.multiple ? (
              <div>
                <select
                  value=""
                  onChange={(event) => {
                    const value = event.target.value
                    if (value) update(flag.name, [...selectedValues, value])
                  }}
                >
                  <option value="">Pievienot vērtību</option>
                  {flag.values.filter((value) => !selectedValues.includes(value)).map((value) => (
                    <option key={value} value={value}>{value}</option>
                  ))}
                </select>
                <div className="selected-flag-values">
                  {selectedValues.map((value) => (
                    <button
                      key={value}
                      type="button"
                      onClick={() => update(flag.name,
                        selectedValues.filter((selectedValue) => selectedValue !== value))}
                    >
                      {value} ×
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              <select
                value={typeof currentValue === 'string' ? currentValue : ''}
                onChange={(event) => update(flag.name, event.target.value || null)}
              >
                <option value="">Izvēlieties</option>
                {flag.values.map((option) => <option key={option} value={option}>{option}</option>)}
              </select>
            )
          ) : (
            <input
              value={Array.isArray(currentValue)
                ? currentValue.join(' | ')
                : String(currentValue ?? '')}
              onChange={(event) => update(flag.name, flag.multiple
                ? event.target.value.split('|').map((item) => item.trim()).filter(Boolean)
                : event.target.value)}
              placeholder={flag.multiple ? 'Vērtības atdaliet ar |' : 'Vērtība'}
            />
          )}
          <button type="button" onClick={() => remove(flag.name)}>Noņemt</button>
        </div>
        )
      })}
    </div>
  )
}
