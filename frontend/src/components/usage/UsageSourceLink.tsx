import { LinkIcon } from '../shared/LinkIcon'

type Props = {
  label: string
  url: string
  originalSource: boolean
}

export function UsageSourceLink({ label, url, originalSource }: Props) {
  const linkTitle = originalSource ? 'Atvērt sākotnējo avotu jaunā cilnē' : 'Atvērt korpusa rezultātus jaunā cilnē'

  return <small className="usage-source">
    <span>{label}</span>{' '}
    <a className="icon-button" href={url} target="_blank" rel="noreferrer" title={linkTitle} aria-label={linkTitle}>
      <LinkIcon />
    </a>
  </small>
}
