import { tezaursEntryUrl } from '../config/tezaurs'

export function TezaursEntryLink({ humanKey }: { humanKey: string }) {
  const label = `Atvērt ${humanKey} Tēzaurā (jaunā cilnē)`
  return (
    <a className="icon-button tezaurs-entry-link" href={tezaursEntryUrl(humanKey)}
      onClick={(event) => event.stopPropagation()}
      target="_blank" rel="noreferrer" aria-label={label} title={label}>
      <span aria-hidden="true">↗</span>
    </a>
  )
}
