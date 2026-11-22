type Props = {
  selected: boolean
  disabled: boolean
  label: string
  color?: 'magenta' | 'green'
  onToggle?: () => void
}

export function SideToggle({ selected, disabled, label, color = 'magenta', onToggle }: Props) {
  return <button className={color === 'green' ? 'side-toggle side-toggle-green' : 'side-toggle'}
    type="button" aria-pressed={selected} aria-label={label} title={label} disabled={disabled} onClick={onToggle} />
}
