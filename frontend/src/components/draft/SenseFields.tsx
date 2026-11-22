import type { SaveSense, TezaursFormOptions, TezaursSense } from '../../api/types'
import { FlagEditor } from './FlagEditor'

type Props = {
  sense: SaveSense
  options: TezaursFormOptions
  existingSenses: TezaursSense[]
  draftSenses: SaveSense[]
  onlyParent?: boolean
  onChange: (sense: SaveSense) => void
  onParentChange: (value: string) => void
}

export function SenseFields({ sense, options, existingSenses, draftSenses, onlyParent = false, onChange, onParentChange }: Props) {
  let parentValue = ''
  if (sense.parentDraftSenseId) {
    parentValue = `draft:${sense.parentDraftSenseId}`
  } else if (sense.parentTezaursSenseId) {
    parentValue = `tezaurs:${sense.parentTezaursSenseId}`
  }
  const existingParents = existingSenses.filter((item) => item.parentSenseId === null)
  const draftParents = draftSenses.filter((item) => item.id && item.id !== sense.id
    && item.parentDraftSenseId === null && item.parentTezaursSenseId === null)
  const hasChildren = sense.id !== undefined && draftSenses.some((item) => item.parentDraftSenseId === sense.id)

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
      {(!hasChildren && (existingParents.length > 0 || draftParents.length > 0)) && (
        <label>
          Virsnozīme
          <select className="custom-select custom-select-sm" value={parentValue} onChange={(event) => onParentChange(event.target.value)}>
            <option value="">Nav</option>
            {existingParents.map((item) => (
              <option key={`tezaurs-${item.id}`} value={`tezaurs:${item.id}`}>
                Esošā: {item.gloss || item.id}
              </option>
            ))}
            {draftParents.map((item) => (
              <option key={`draft-${item.id}`} value={`draft:${item.id}`}>
                Melnraksta: {item.gloss}
              </option>
            ))}
          </select>
        </label>
      )}
      {!onlyParent && (
        <FlagEditor
          definitions={options.flags}
          flags={sense.flags ?? {}}
          onChange={(flags) => onChange({ ...sense, flags })}
          scope="S"
        />
      )}
    </>
  )
}
