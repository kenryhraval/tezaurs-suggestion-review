import { useEffect, useId, useRef, useState } from 'react'
import { EditButton } from '../shared/EditButton'

type Action = {
  label: string
  onClick?: () => void
}

type ActionsProps = {
  kind: 'entry' | 'lexeme' | 'sense'
  label: string
  onEdit?: () => void
  readOnly?: boolean
  items: Action[]
}

function EntityActions({ kind, label, onEdit, items, readOnly = false }: ActionsProps) {
  const buttonVariants = { entry: 'primary', lexeme: 'warning', sense: 'success' }
  const buttonClassName = readOnly ? 'btn btn-sm btn-secondary' : `btn btn-sm btn-${buttonVariants[kind]}`
  const [open, setOpen] = useState(false)
  const container = useRef<HTMLSpanElement>(null)
  const toggle = useRef<HTMLButtonElement>(null)
  const menuId = useId()
  const hasMenuItems = items.length > 0

  useEffect(() => {
    if (!open) {
      return
    }
    function closeOutside(event: PointerEvent) {
      if (!container.current?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('pointerdown', closeOutside)
    return () => document.removeEventListener('pointerdown', closeOutside)
  }, [open])

  return (
    <span className="btn-group btn-group-sm entity-actions" ref={container}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) {
          setOpen(false)
        }
      }} onKeyDown={(event) => {
        if (event.key === 'Escape') {
          event.stopPropagation()
          setOpen(false)
          toggle.current?.focus()
        }
      }}>
      <EditButton className={buttonClassName} icon={readOnly ? 'info' : 'edit'}
        label={`${readOnly ? 'Skatīt' : 'Rediģēt'} ${label}`} disabled={!onEdit} onClick={() => onEdit?.()} />
      {hasMenuItems && <button className={`${buttonClassName} dropdown-toggle`} type="button" ref={toggle}
        title="Papildu darbības" aria-label={`Papildu darbības: ${label}`}
        aria-expanded={open} aria-controls={menuId} onClick={() => setOpen(!open)} />}
      {open && hasMenuItems && (
        <span className="dropdown-menu show entity-action-menu" id={menuId}>
          {items.map((item) => (
            <button key={item.label} className="dropdown-item" type="button" disabled={!item.onClick}
              onClick={() => {
                setOpen(false)
                toggle.current?.focus()
                item.onClick?.()
              }}>{item.label}</button>
          ))}
        </span>
      )}
    </span>
  )
}

type EntryProps = {
  onEdit?: () => void
  readOnly?: boolean
  onAddLexeme?: () => void
  onAddSense?: () => void
}

export function EntryActions({ onEdit, onAddLexeme, onAddSense, readOnly }: EntryProps) {
  return <EntityActions kind="entry" label="šķirkli" onEdit={onEdit} readOnly={readOnly} items={[
    { label: 'Pievienot leksēmu', onClick: onAddLexeme },
    { label: 'Pievienot nozīmi', onClick: onAddSense },
  ]} />
}

type LexemeProps = {
  onEdit?: () => void
  readOnly?: boolean
  onDelete?: () => void
}

export function LexemeActions({ onEdit, onDelete, readOnly }: LexemeProps) {
  return <EntityActions kind="lexeme" label="leksēmu" onEdit={onEdit} readOnly={readOnly} items={[
    { label: 'Dzēst leksēmu', onClick: onDelete },
  ]} />
}

type SenseProps = {
  subsense: boolean
  onEdit?: () => void
  readOnly?: boolean
  onAddSubsense?: () => void
  onMove?: () => void
  onPromote?: () => void
  onDelete?: () => void
}

export function SenseActions({ subsense, onEdit, onAddSubsense, onMove, onPromote, onDelete, readOnly }: SenseProps) {
  const items: Action[] = subsense ? [
    { label: 'Pārvietot apakšnozīmi zem citas nozīmes', onClick: onMove },
    { label: 'Pārvietot apakšnozīmi par patstāvīgu nozīmi', onClick: onPromote },
    { label: 'Dzēst apakšnozīmi', onClick: onDelete },
  ] : [
    { label: 'Pievienot apakšnozīmi', onClick: onAddSubsense },
    { label: 'Pārvietot nozīmi zem citas nozīmes', onClick: onMove },
    { label: 'Dzēst nozīmi', onClick: onDelete },
  ]
  return <EntityActions kind="sense" label={subsense ? 'apakšnozīmi' : 'nozīmi'} onEdit={onEdit} readOnly={readOnly} items={items} />
}
