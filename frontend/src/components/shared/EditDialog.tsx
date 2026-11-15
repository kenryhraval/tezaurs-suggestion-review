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
  readOnly?: boolean
}

export function EditDialog({ title, busy, error, onSubmit, onCancel, children, wide = true, readOnly = false }: Props) {
  return (
    <Dialog title={title} busy={busy} wide={wide} onCancel={onCancel}>
      <form className="entry-draft-form" onSubmit={(event) => {
        event.preventDefault()
        if (!busy && !readOnly) {
          onSubmit(event)
        }
      }}>
        {readOnly ? <fieldset disabled className="read-only-fields">{children}</fieldset> : children}
        {error && <p className="error" role="alert">{error}</p>}
        <div className="modal-footer">
          <button className="btn btn-sm btn-secondary" disabled={busy} onClick={onCancel} type="button">{readOnly ? 'Aizvērt' : 'Atcelt'}</button>
          {!readOnly && <button className="btn btn-sm btn-primary" disabled={busy} aria-busy={busy} type="submit">
            {busy ? 'Saglabā...' : 'Saglabāt'}
          </button>}
        </div>
      </form>
    </Dialog>
  )
}
