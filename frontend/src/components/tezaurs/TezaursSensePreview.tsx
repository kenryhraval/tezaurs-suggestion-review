import type { ReactNode } from 'react'
import type { TezaursSense } from '../../api/types'

type Props = {
  senses: TezaursSense[]
}

export function TezaursSensePreview({ senses }: Props) {
  const rootSenses = senses.filter((sense) => sense.parentSenseId === null
    || !senses.some((parent) => parent.id === sense.parentSenseId))

  function renderSenses(siblings: TezaursSense[], prefix: string): ReactNode {
    if (siblings.length === 0) {
      return null
    }
    return (
      <ol className="tezaurs-sense-preview">
        {siblings.map((sense, index) => {
          const number = prefix + (index + 1)
          const children = senses.filter((child) => child.parentSenseId === sense.id)
          return (
            <li key={sense.id}>
              <strong className="text-muted">{number}.</strong> {sense.gloss}
              {renderSenses(children, number + '.')}
            </li>
          )
        })}
      </ol>
    )
  }

  return renderSenses(rootSenses, '')
}
