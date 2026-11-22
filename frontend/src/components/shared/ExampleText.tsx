import type { Example } from '../../api/types'

type Props = {
  example: Example
}

export function ExampleText({ example }: Props) {
  const { citation, matchStart, matchEnd } = example
  if (matchStart == null || matchEnd == null) return <>{citation}</>

  return <>
    {citation.slice(0, matchStart)}
    <strong>{citation.slice(matchStart, matchEnd)}</strong>
    {citation.slice(matchEnd)}
  </>
}
