import type { SaveSenseDraft, TezaursFormOptions, TezaursSense } from '../../api/types'
import { FlagEditor } from './FlagEditor'
import { RestrictionEditor } from './RestrictionEditor'

type Props = {
  sense: SaveSenseDraft
  options: TezaursFormOptions
  existingSenses: TezaursSense[]
  draftSenses: SaveSenseDraft[]
  onlyParent?: boolean
  onChange: (sense: SaveSenseDraft) => void
  onParentChange: (value: string) => void
}

export function SenseFields({ sense, options, existingSenses, draftSenses, onlyParent = false, onChange, onParentChange }: Props) {
  let parentValue = ''
  if (sense.parentDraftSenseId) {
    parentValue = `draft:${sense.parentDraftSenseId}`
  } else if (sense.parentTezaursSenseId) {
    parentValue = `tezaurs:${sense.parentTezaursSenseId}`
  }
  return (
    <>
      {!onlyParent && <label>
        Skaidrojums
        <textarea className="form-control form-control-sm"
          autoFocus
          onChange={(event) => onChange({
            ...sense,
            gloss: event.target.value,
          })}
          required
          rows={4}
          value={sense.gloss}
        />
      </label>}
      {(existingSenses.length > 0 || draftSenses.some((item) => item.id && item.id !== sense.id)) && (
        <label>
          Virsnozīme
          <select className="custom-select custom-select-sm" value={parentValue} onChange={(event) => onParentChange(event.target.value)}>
            <option value="">Nav</option>
            {existingSenses.map((item) => (
              <option key={`tezaurs-${item.id}`} value={`tezaurs:${item.id}`}>
                Esošā: {item.gloss || item.id}
              </option>
            ))}
            {draftSenses.filter((item) => item.id && item.id !== sense.id).map((item) => (
              <option key={`draft-${item.id}`} value={`draft:${item.id}`}>
                Melnraksta: {item.gloss}
              </option>
            ))}
          </select>
        </label>
      )}
      {!onlyParent && <>
        <FlagEditor
          definitions={options.flags}
          flags={sense.flags}
          onChange={(flags) => onChange({ ...sense, flags })}
          scope="S"
        />
        <RestrictionEditor
          definitions={options.flags}
          frequencies={options.restrictionFrequencies}
          onChange={(structuralRestrictions) => onChange({
            ...sense,
            structuralRestrictions,
          })}
          restriction={sense.structuralRestrictions}
          types={options.restrictionTypes}
        />
      </>}
    </>
  )
}
