import type { SaveSense, TezaursFormOptions } from '../../api/types'
import { FlagEditor } from './FlagEditor'

type Props = {
  sense: SaveSense
  options: TezaursFormOptions
  onChange: (sense: SaveSense) => void
}

export function SenseFields({ sense, options, onChange }: Props) {
  return <>
    <label>
      Skaidrojums
      <textarea className="form-control form-control-sm" autoFocus required rows={4} maxLength={10000}
        value={sense.gloss} onChange={(event) => onChange({ ...sense, gloss: event.target.value })} />
    </label>
    <FlagEditor definitions={options.flags} flags={sense.flags ?? {}}
      onChange={(flags) => onChange({ ...sense, flags })} scope="S" />
  </>
}
