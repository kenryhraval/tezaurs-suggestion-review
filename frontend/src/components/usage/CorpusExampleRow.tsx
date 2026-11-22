import type { ReactNode } from 'react'
import { UsageSourceLink } from './UsageSourceLink'
import { SideToggle } from '../shared/SideToggle'

type Props = {
  selected: boolean
  busy: boolean
  sourceLabel: string
  sourceUrl: string
  originalSource: boolean
  onToggle?: () => void
  children: ReactNode
}

export function CorpusExampleRow({ selected, busy, sourceLabel, sourceUrl, originalSource, onToggle, children }: Props) {
  let actionLabel = selected ? 'Noņemt piemēra atzīmi' : 'Atzīmēt piemēru'
  if (selected && !onToggle) actionLabel = 'Atzīmēts piemērs'

  return <article className="corpus-example-row">
    <SideToggle selected={selected} label={actionLabel} disabled={busy || !onToggle} onToggle={onToggle} />
    <p>{children}</p>
    <UsageSourceLink label={sourceLabel} url={sourceUrl} originalSource={originalSource} />
  </article>
}
