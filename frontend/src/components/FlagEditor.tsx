import type { FlagMap, FlagValue, TezaursFlagDefinition } from '../api/types'

type Props = {
  scope: 'E' | 'L' | 'R' | 'S'
  definitions: TezaursFlagDefinition[]
  flags: FlagMap
  onChange: (value: FlagMap) => void
}

export function FlagEditor({ scope, definitions, flags, onChange }: Props) {
  const availableFlags = definitions.filter((flag) => flag.scopes.includes(scope))
  const selectedFlags = availableFlags.filter((flag) => flag.name in flags)
  const unselectedFlags = availableFlags.filter((flag) => !(flag.name in flags))

  function addFlag(name: string) {
    if (!name) {
      return
    }
    const flag = availableFlags.find((item) => item.name === name)
    if (!flag) {
      return
    }

    const initialValue: FlagValue = flag.multiple ? [] : null
    const updatedFlags = { ...flags }
    updatedFlags[name] = initialValue
    onChange(updatedFlags)
  }

  function updateFlag(name: string, value: FlagValue) {
    const updatedFlags = { ...flags }
    updatedFlags[name] = value
    onChange(updatedFlags)
  }

  function removeFlag(name: string) {
    const updatedFlags = { ...flags }
    delete updatedFlags[name]
    onChange(updatedFlags)
  }

  function updateFreeText(flag: TezaursFlagDefinition, text: string) {
    if (flag.multiple) {
      const values = text.split('|').map((value) => value.trim()).filter((value) => value !== '')
      updateFlag(flag.name, values)
    } else {
      updateFlag(flag.name, text)
    }
  }

  function renderFlagInput(flag: TezaursFlagDefinition) {
    const currentValue = flags[flag.name]
    const selectedValues = Array.isArray(currentValue) ? currentValue : []
    const hasPredefinedValues = flag.permittedValues === 'E'

    if (!hasPredefinedValues) {
      let inputText = String(currentValue ?? '')
      if (Array.isArray(currentValue)) {
        inputText = currentValue.join(' | ')
      }
      return (
        <input value={inputText} onChange={(event) => updateFreeText(flag, event.target.value)}
          placeholder={flag.multiple ? 'Vērtības atdaliet ar |' : 'Vērtība'} />
      )
    }

    if (!flag.multiple) {
      return (
        <select value={typeof currentValue === 'string' ? currentValue : ''}
          onChange={(event) => updateFlag(flag.name, event.target.value || null)}>
          <option value="">Izvēlieties</option>
          {flag.values.map((value) => <option key={value} value={value}>{value}</option>)}
        </select>
      )
    }

    const remainingValues = flag.values.filter((value) => !selectedValues.includes(value))

    function addValue(value: string) {
      if (value) {
        updateFlag(flag.name, [...selectedValues, value])
      }
    }

    function removeValue(value: string) {
      const remainingSelections = selectedValues.filter((selectedValue) => selectedValue !== value)
      updateFlag(flag.name, remainingSelections)
    }

    return (
      <div>
        <select value="" onChange={(event) => addValue(event.target.value)}>
          <option value="">Pievienot vērtību</option>
          {remainingValues.map((value) => <option key={value} value={value}>{value}</option>)}
        </select>
        <div className="selected-flag-values">
          {selectedValues.map((value) => (
            <button key={value} type="button" onClick={() => removeValue(value)}>{value} ×</button>
          ))}
        </div>
      </div>
    )
  }

  return (
    <div className="flag-editor">
      <label>
        Pazīmes
        <select value="" onChange={(event) => addFlag(event.target.value)}>
          <option value="">Pievienot pazīmi</option>
          {unselectedFlags.map((flag) => (
            <option key={flag.name} value={flag.name}>{flag.label}</option>
          ))}
        </select>
      </label>

      {selectedFlags.map((flag) => (
        <div className="flag-row" key={flag.name} title={flag.description ?? undefined}>
          <span>{flag.label}</span>
          {renderFlagInput(flag)}
          <button type="button" onClick={() => removeFlag(flag.name)}>Noņemt</button>
        </div>
      ))}
    </div>
  )
}
