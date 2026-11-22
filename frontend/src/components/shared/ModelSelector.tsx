import { useId, useRef, useState } from 'react'
import type { SenseModelOption } from '../../api/types'

type Props = {
  models: SenseModelOption[]
  value: string
  disabled: boolean
  onChange: (modelId: string) => void
}

export function ModelSelector({ models, value, disabled, onChange }: Props) {
  const [open, setOpen] = useState(false)
  const menuId = useId()
  const toggleRef = useRef<HTMLButtonElement>(null)
  const selected = models.find((model) => model.id === value)

  function chooseModel(modelId: string) {
    onChange(modelId)
    setOpen(false)
    toggleRef.current?.focus()
  }

  return <div className="tezaurs-dropdown sense-model-select" onBlur={(event) => {
    if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false)
  }} onKeyDown={(event) => {
    if (event.key === 'Escape') {
      event.stopPropagation()
      setOpen(false)
      toggleRef.current?.focus()
    }
  }}>
    <div className="tezaurs-dropdown-control">
      <button className="tezaurs-dropdown-toggle" ref={toggleRef} type="button" disabled={disabled}
        aria-label="MI modelis" aria-expanded={open && !disabled} aria-controls={menuId} onClick={() => setOpen(!open)}>
        <span className="tezaurs-option-label">{selected?.label ?? 'MI modeļi nav pieejami'}</span>
        <span aria-hidden="true">▾</span>
      </button>
    </div>
    {open && !disabled && <div className="tezaurs-dropdown-menu" id={menuId}>
      <div className="tezaurs-dropdown-options">
        {models.map((model) => <button type="button" key={model.id}
          aria-current={model.id === value ? 'true' : undefined} onClick={() => chooseModel(model.id)}>
          {model.label}
        </button>)}
      </div>
    </div>}
  </div>
}
