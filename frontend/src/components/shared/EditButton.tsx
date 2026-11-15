type Props = {
  label: string
  disabled?: boolean
  onClick: () => void
  className?: string
  icon?: 'edit' | 'info'
}

export function EditButton({ label, disabled, onClick, className = 'icon-button', icon = 'edit' }: Props) {
  return (
    <button type="button" className={`${className} edit-button`} title={label} aria-label={label}
      disabled={disabled} onClick={onClick}>
      {icon === 'info' ? (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none"
          stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" aria-hidden="true">
          <circle cx="12" cy="12" r="9" />
          <path d="M12 11v6" />
          <circle cx="12" cy="7" r="0.75" fill="currentColor" stroke="none" />
        </svg>
      ) : (
        <svg width="14" height="14" viewBox="0 0 512 512" fill="currentColor" aria-hidden="true">
          {/* Font Awesome Free 5.9.0, Fonticons, Inc. — CC BY 4.0: https://fontawesome.com/license/free */}
          <path d="M290.74 93.24l128.02 128.02-277.99 277.99-114.14 12.6C11.35 513.54-1.56 500.62.14 485.34l12.7-114.22 277.9-277.88zm207.2-19.06l-60.11-60.11c-18.75-18.75-49.16-18.75-67.91 0l-56.55 56.55 128.02 128.02 56.55-56.55c18.75-18.76 18.75-49.16 0-67.91z" />
        </svg>
      )}
    </button>
  )
}
