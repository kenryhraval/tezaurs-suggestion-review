import { useState } from 'react'
import type { ReviewStatus } from '../../api/types'

type Props = {
  status: ReviewStatus
  canComplete: boolean
  busy: boolean
  onComplete: (status: 'NEEDS_EXPERT' | 'COMPLETED') => void
}

export function ReviewCompletion({ status, canComplete, busy, onComplete }: Props) {
  const editable = status === 'IN_PROGRESS'
  const [expandedByUser, setExpanded] = useState(editable)
  const expanded = expandedByUser || editable
  const finished = status === 'COMPLETED' || status === 'ALREADY_EXISTS'

  return (
    <details className="completion-section review-section" open={expanded}
      onToggle={(event) => setExpanded(event.currentTarget.open)}>
      <summary className={!expanded && finished ? 'section-title confirmed' : 'section-title'}
        aria-disabled={editable} onClick={(event) => {
          if (editable) event.preventDefault()
        }}>
        5. Darba pabeigšana
      </summary>
      <p className="muted">Vai uzskatāt, ka nepieciešams eksperta apstiprinājums?</p>
      <div className="tezaurs-answer-actions">
        <button className="btn btn-sm btn-outline-secondary" type="button" disabled={!editable || busy}
          onClick={() => onComplete('NEEDS_EXPERT')}>Jā, vajag</button>
        <button className="btn btn-sm btn-outline-secondary" type="button" disabled={!editable || busy || !canComplete}
          onClick={() => onComplete('COMPLETED')}>Nē, nevajag</button>
      </div>
    </details>
  )
}
