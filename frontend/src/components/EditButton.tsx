type Props = {
  label: string
  disabled?: boolean
  onClick: () => void
  className?: string
}

export function EditButton({ label, disabled, onClick, className = 'icon-button' }: Props) {
  return (
    <button type="button" className={`${className} edit-button`} title={label} aria-label={label}
      disabled={disabled} onClick={onClick}>
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
        stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="m16 3 5 5-13 13H3v-5L16 3Zm-3 3 5 5" />
      </svg>
    </button>
  )
}
