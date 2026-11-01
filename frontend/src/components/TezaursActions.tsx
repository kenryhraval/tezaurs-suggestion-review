import { Fragment, useEffect, useId, useRef, useState } from 'react'
import { tezaursEntryUrl } from '../config/tezaurs'
import { EditButton } from './EditButton'

type Action = {
  label: string
  onClick?: () => void
  href?: string
  separator?: boolean
}

type ActionsProps = {
  kind: 'entry' | 'lexeme' | 'sense'
  label: string
  onEdit?: () => void
  items: Action[]
}

function EntityActions({ kind, label, onEdit, items }: ActionsProps) {
  const [open, setOpen] = useState(false)
  const container = useRef<HTMLSpanElement>(null)
  const toggle = useRef<HTMLButtonElement>(null)
  const menuId = useId()

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
    <span className={`entity-actions entity-actions-${kind}`} ref={container}
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
      <EditButton label={`Rediģēt ${label}`} disabled={!onEdit} onClick={() => onEdit?.()} />
      <button className="action-toggle" type="button" ref={toggle}
        title="Papildu darbības" aria-label={`Papildu darbības: ${label}`}
        aria-expanded={open} aria-controls={menuId} onClick={() => setOpen(!open)}>
        <span aria-hidden="true">▾</span>
      </button>
      {open && (
        <span className="entity-action-menu" id={menuId}>
          {items.map((item) => (
            <Fragment key={item.label}>
              {item.separator && <span className="entity-action-separator" aria-hidden="true" />}
              {item.href ? (
                <a href={item.href} target="_blank" rel="noreferrer" onClick={() => setOpen(false)}>{item.label}</a>
              ) : (
                <button type="button" disabled={!item.onClick} onClick={() => {
                  setOpen(false)
                  toggle.current?.focus()
                  item.onClick?.()
                }}>{item.label}</button>
              )}
            </Fragment>
          ))}
        </span>
      )}
    </span>
  )
}

type EntryProps = {
  onEdit?: () => void
  onAddLexeme?: () => void
  onAddSense?: () => void
  publicHumanKey?: string
}

export function EntryActions({ onEdit, onAddLexeme, onAddSense, publicHumanKey }: EntryProps) {
  return <EntityActions kind="entry" label="šķirkli" onEdit={onEdit} items={[
    { label: 'Pievienot leksēmu', onClick: onAddLexeme },
    { label: 'Pievienot nozīmi', onClick: onAddSense },
    { label: 'Pievienot saiti uz stabilu vārdu savienojumu' },
    { label: 'Pievienot saiti uz saistītu šķirkli' },
    { label: 'Semantiskās saites' },
    { label: 'Pievienot šo šķirkli citam šķirklim' },
    { label: 'Pievienot šķirkļa avotu' },
    { label: 'Dzēst šķirkli' },
    { label: 'Labošanas vēsture', separator: true },
    { label: 'Publiskā versija', separator: true,
      href: publicHumanKey ? tezaursEntryUrl(publicHumanKey) : undefined },
  ]} />
}

type LexemeProps = {
  onEdit?: () => void
  onDelete?: () => void
}

export function LexemeActions({ onEdit, onDelete }: LexemeProps) {
  return <EntityActions kind="lexeme" label="leksēmu" onEdit={onEdit} items={[
    { label: 'Pārvietot leksēmu' },
    { label: 'Pievienot saiti no leksēmas uz tās morfoloģisko atvasinājumu' },
    { label: 'Pievienot leksēmas avotu' },
    { label: 'Pavairot leksēmu' },
    { label: 'Pievienot izņēmumformu' },
    { label: 'Dzēst leksēmu', onClick: onDelete },
  ]} />
}

type SenseProps = {
  subsense: boolean
  onEdit?: () => void
  onAddSubsense?: () => void
  onMove?: () => void
  onPromote?: () => void
  onDelete?: () => void
}

export function SenseActions({ subsense, onEdit, onAddSubsense, onMove, onPromote, onDelete }: SenseProps) {
  const items: Action[] = subsense ? [
    { label: 'Pievienot piemēru' },
    { label: 'Pievienot saiti uz stabilu vārdu savienojumu' },
    { label: 'Pārvietot apakšnozīmi' },
    { label: 'Pārvietot apakšnozīmi zem citas nozīmes', onClick: onMove },
    { label: 'Pārvietot apakšnozīmi par patstāvīgu nozīmi', onClick: onPromote },
    { label: 'Pievienot apakšnozīmes avotu' },
    { label: 'Dzēst apakšnozīmi', onClick: onDelete },
  ] : [
    { label: 'Pievienot apakšnozīmi', onClick: onAddSubsense },
    { label: 'Pievienot piemēru' },
    { label: 'Pievienot saiti uz stabilu vārdu savienojumu' },
    { label: 'Pārvietot nozīmi' },
    { label: 'Pārvietot nozīmi zem citas nozīmes', onClick: onMove },
    { label: 'Pievienot nozīmes avotu' },
    { label: 'Dzēst nozīmi', onClick: onDelete },
  ]
  return (
    <>
      <EntityActions kind="sense" label={subsense ? 'apakšnozīmi' : 'nozīmi'} onEdit={onEdit} items={items} />
      <button type="button" className="synonym-action" title="Sinonīmu kopa" aria-label="Sinonīmu kopa" disabled>
        <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <path d="M2 3h7v6H2zM15 3h7v6h-7zM15 15h7v6h-7zM9 5h6v2H9zM7 9h2v3h8v3h-2v-1H7z" />
        </svg>
      </button>
    </>
  )
}
