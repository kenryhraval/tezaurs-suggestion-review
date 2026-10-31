type Props = {
  heading: string
  humanKey: string
}

export function EntryHeading({ heading, humanKey }: Props) {
  const homonymNumber = humanKey.startsWith('mwe:') ? 1 : Number(humanKey.match(/:(\d+)$/)?.[1] ?? 1)
  return <strong>{heading}{homonymNumber > 1 && <sup>{homonymNumber}</sup>}</strong>
}
