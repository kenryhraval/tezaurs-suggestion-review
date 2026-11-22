import { tezaursEntryUrl } from '../../config/tezaurs'
import { LinkIcon } from '../shared/LinkIcon'

export function TezaursEntryLink({ humanKey }: { humanKey: string }) {
  const label = `Atvērt ${humanKey} Tēzaurā (jaunā cilnē)`
  return (
    <a className="icon-button tezaurs-entry-link" href={tezaursEntryUrl(humanKey)}
      onClick={(event) => event.stopPropagation()}
      target="_blank" rel="noreferrer" aria-label={label} title={label}>
      <LinkIcon />
    </a>
  )
}
