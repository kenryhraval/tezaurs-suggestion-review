import { useEffect, useRef, type FormEvent, type KeyboardEvent, type ReactNode } from 'react'
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

  function handleKeyDown(event: KeyboardEvent<HTMLFormElement>) {
    if (event.key !== 'Enter') {
      return
    }

    const input = event.target
    if (!(input instanceof HTMLInputElement)) {
      return
    }

    const isChoiceInput = input.type === 'checkbox' || input.type === 'radio'
    if (isChoiceInput) {
      event.preventDefault()
      event.currentTarget.requestSubmit()
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!busy) {
      onConfirm()
    }
  }

  return (
    <Dialog title={title} busy={busy} onCancel={onCancel}>
      <form onKeyDown={handleKeyDown} onSubmit={handleSubmit}>
        {message && <p>{message}</p>}
        {children}
        {error && <p className="error" role="alert">{error}</p>}
        <div className="modal-footer">
          <button className="btn btn-sm btn-light border" disabled={busy} onClick={onCancel} type="button">Atcelt</button>
          <button ref={confirmButtonRef} className="btn btn-sm btn-light border text-danger" aria-busy={busy} disabled={busy}
            type="submit">Apstiprināt</button>
        </div>
      </form>
    </Dialog>
  )
}
