import type { FormEvent, ReactNode } from 'react'
import { Dialog } from './Dialog'

type Props = {
  title: string
  busy: boolean
  error: string
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  onCancel: () => void
  children: ReactNode
  wide?: boolean
}

export function EditDialog({ title, busy, error, onSubmit, onCancel, children, wide = true }: Props) {
  return (
    <Dialog title={title} busy={busy} wide={wide} onCancel={onCancel}>
      <form className="entry-draft-form" onSubmit={(event) => {
        event.preventDefault()
        if (!busy) {
          onSubmit(event)
        }
      }}>
        {children}
        {error && <p className="error" role="alert">{error}</p>}
        <div className="modal-footer">
          <button className="btn btn-sm btn-secondary" disabled={busy} onClick={onCancel} type="button">Atcelt</button>
          <button className="btn btn-sm btn-primary" disabled={busy} aria-busy={busy} type="submit">
            {busy ? 'Saglabā...' : 'Saglabāt'}
          </button>
        </div>
      </form>
    </Dialog>
  )
}
