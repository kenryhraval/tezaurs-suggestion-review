import { useEffect, useId, useRef, type ReactNode } from 'react'

type Props = {
  title: string
  busy: boolean
  wide?: boolean
  onCancel: () => void
  children: ReactNode
}

export function Dialog({ title, busy, wide = false, onCancel, children }: Props) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const dialog = dialogRef.current
    dialog?.showModal()
    return () => dialog?.close()
  }, [])

  return (
    <dialog className={wide ? 'confirmation-dialog edit-dialog' : 'confirmation-dialog'}
      ref={dialogRef} aria-labelledby={titleId} onCancel={(event) => {
        event.preventDefault()
        if (!busy) {
          onCancel()
        }
      }}>
      <h3 id={titleId}>{title}</h3>
      {children}
    </dialog>
  )
}
