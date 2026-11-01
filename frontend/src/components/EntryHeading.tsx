type Props = {
  heading: string
  humanKey: string
}

export function EntryHeading({ heading, humanKey }: Props) {
  const isMultiwordEntry = humanKey.startsWith('mwe:')
  const numberSeparatorIndex = humanKey.lastIndexOf(':')
  let homonymNumber = 1

  if (!isMultiwordEntry && numberSeparatorIndex !== -1) {
    const numberText = humanKey.slice(numberSeparatorIndex + 1)
    homonymNumber = Number(numberText)
  }

  const showHomonymNumber = Number.isInteger(homonymNumber) && homonymNumber > 1

  return (
    <strong>
      {heading}
      {showHomonymNumber && <sup>{homonymNumber}</sup>}
    </strong>
  )
}
