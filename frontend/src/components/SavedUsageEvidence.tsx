import type { UsageEvidence } from '../api/types'
import type { ReactNode } from 'react'
import { UsageSourceLink } from './UsageSourceLink'

type Props = {
  evidence: UsageEvidence
  busy: boolean
  onRemove?: () => void
  highlighted?: boolean
  children?: ReactNode
}

export function SavedUsageEvidence({ evidence, busy, onRemove, highlighted = false, children }: Props) {
  return (
    <article className={highlighted ? 'usage-evidence marked-evidence' : 'usage-evidence'}>
      <div className="evidence-text">
        <p>{children ?? evidence.citation}</p>
        {onRemove && <button type="button" className="icon-button remove-evidence" title="Noņemt atzīmi"
          aria-label="Noņemt atzīmi" disabled={busy} onClick={onRemove}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" aria-hidden="true">
            <path d="m6 6 12 12M6 18 18 6" />
          </svg>
        </button>}
      </div>
      <UsageSourceLink label={evidence.sourceLabel} url={evidence.sourceUrl ?? evidence.url}
        originalSource={evidence.sourceUrl !== null} />
    </article>
  )
}
