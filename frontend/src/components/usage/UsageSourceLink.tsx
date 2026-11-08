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
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M15 3h6v6M10 14 21 3M21 14v7H3V3h7" />
      </svg>
    </a>
  </small>
}
