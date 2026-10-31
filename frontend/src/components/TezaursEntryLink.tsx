import { tezaursEntryUrl } from '../tezaurs'

export function TezaursEntryLink({ humanKey }: { humanKey: string }) {
  const label = `Atvērt ${humanKey} Tēzaurā (jaunā cilnē)`
  return (
    <a className="tezaurs-entry-link" href={tezaursEntryUrl(humanKey)}
      target="_blank" rel="noreferrer" aria-label={label} title={label}>
      <span aria-hidden="true">↗</span>
    </a>
  )
}
