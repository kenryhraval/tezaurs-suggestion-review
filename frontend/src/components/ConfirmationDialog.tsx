import type { ReactNode } from 'react'
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
  return (
    <Dialog title={title} busy={busy} onCancel={onCancel}>
      <form onSubmit={(event) => {
        event.preventDefault()
        if (!busy) onConfirm()
      }}>
        {message && <p>{message}</p>}
        {children}
        {error && <p className="error" role="alert">{error}</p>}
        <div className="confirmation-actions">
          <button autoFocus={!children} disabled={busy} onClick={onCancel} type="button">Atcelt</button>
          <button className="confirmation-confirm" aria-busy={busy} disabled={busy}
            type="submit">Apstiprināt</button>
        </div>
      </form>
    </Dialog>
  )
}
