import { useEffect, useRef, type ReactNode } from 'react'
import { Dialog } from './Dialog'

type Props = {
  message: string
  title?: string
  children?: ReactNode
  busy: boolean
  error?: string
  onConfirm: () => void
  onCancel: () => void
}

export function ConfirmationDialog({
  message, title = 'Apstiprināt izvēli', children, busy, error, onConfirm, onCancel,
}: Props) {
  const confirmButtonRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!busy) {
      confirmButtonRef.current?.focus({ focusVisible: true })
    }
  }, [busy])

  return (
    <Dialog title={title} busy={busy} onCancel={onCancel}>
      <form onKeyDown={(event) => {
        const input = event.target
        if (event.key === 'Enter' && input instanceof HTMLInputElement
            && (input.type === 'checkbox' || input.type === 'radio')) {
          event.preventDefault()
          event.currentTarget.requestSubmit()
        }
      }} onSubmit={(event) => {
        event.preventDefault()
        if (!busy) {
          onConfirm()
        }
      }}>
        {message && <p>{message}</p>}
        {children}
        {error && <p className="error" role="alert">{error}</p>}
        <div className="confirmation-actions">
          <button disabled={busy} onClick={onCancel} type="button">Atcelt</button>
          <button ref={confirmButtonRef} className="confirmation-confirm" aria-busy={busy} disabled={busy}
            type="submit">Apstiprināt</button>
        </div>
      </form>
    </Dialog>
  )
}
