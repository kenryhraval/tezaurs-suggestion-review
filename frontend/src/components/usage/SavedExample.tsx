import type { Example } from '../../api/types'
import type { ReactNode } from 'react'
import { UsageSourceLink } from './UsageSourceLink'

type Props = {
  example: Example
  busy: boolean
  onRemove?: () => void
  highlighted?: boolean
  children?: ReactNode
}

export function SavedExample({ example, busy, onRemove, highlighted = false, children }: Props) {
  return (
    <article className={highlighted ? 'usage-example marked-example' : 'usage-example'}>
      <div className="example-text">
        <p>{children ?? example.citation}</p>
        {onRemove && <button type="button" className="icon-button remove-example" title="Noņemt atzīmi"
          aria-label="Noņemt atzīmi" disabled={busy} onClick={onRemove}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" aria-hidden="true">
            <path d="m6 6 12 12M6 18 18 6" />
          </svg>
        </button>}
      </div>
      <UsageSourceLink label={example.sourceLabel} url={example.sourceUrl ?? example.url}
        originalSource={example.sourceUrl !== null} />
    </article>
  )
}
