import { useLayoutEffect, useRef } from 'react'

type CorpusChoice = {
  id: string
  label: string
  total: number | null
  error: string
}

type Props = {
  choices: CorpusChoice[]
  disabled?: boolean
  onSelect: (id: string) => void
}

function rowsNeeded(buttonWidths: number[], availableWidth: number, gap: number) {
  let rows = 1
  let rowWidth = 0
  for (const buttonWidth of buttonWidths) {
    const nextWidth = rowWidth === 0 ? buttonWidth : rowWidth + gap + buttonWidth
    if (rowWidth > 0 && nextWidth > availableWidth) {
      rows += 1
      rowWidth = buttonWidth
    } else {
      rowWidth = nextWidth
    }
  }
  return rows
}

export function CorpusSelector({ choices, disabled = false, onSelect }: Props) {
  const viewportRef = useRef<HTMLDivElement>(null)
  const listRef = useRef<HTMLUListElement>(null)

  useLayoutEffect(() => {
    const viewport = viewportRef.current
    const list = listRef.current
    if (!viewport || !list) {
      return
    }

    const fitThreeRows = () => {
      if (viewport.clientWidth === 0 || list.children.length === 0) {
        return
      }
      const availableWidth = viewport.getBoundingClientRect().width
      const widths = Array.from(list.children, (item) => item.getBoundingClientRect().width)
      const gap = Number.parseFloat(getComputedStyle(list).columnGap)
      let widestButton = 0
      let maximumWidth = gap * (widths.length - 1)
      for (const width of widths) {
        widestButton = Math.max(widestButton, width)
        maximumWidth += width
      }
      if (widestButton <= availableWidth && rowsNeeded(widths, availableWidth, gap) <= 3) {
        list.style.width = '100%'
        return
      }
      let minimumWidth = Math.ceil(Math.max(availableWidth, widestButton))
      maximumWidth = Math.max(minimumWidth, Math.ceil(maximumWidth))

      // Find the narrowest continuous strip that fits every button within three rows.
      while (minimumWidth < maximumWidth) {
        const candidateWidth = Math.floor((minimumWidth + maximumWidth) / 2)
        if (rowsNeeded(widths, candidateWidth, gap) > 3) {
          minimumWidth = candidateWidth + 1
        } else {
          maximumWidth = candidateWidth
        }
      }
      list.style.width = `${minimumWidth}px`
    }

    fitThreeRows()
    const observer = new ResizeObserver(fitThreeRows)
    observer.observe(viewport)
    for (const item of list.children) {
      observer.observe(item)
    }
    return () => observer.disconnect()
  }, [choices])

  return (
    <div className="corpus-selector-scroll" ref={viewportRef} role="region" aria-label="Korpusu izvēle" tabIndex={0}>
      <ul className="corpus-badges" ref={listRef} aria-label="Korpusu meklēšanas rezultāti">
        {choices.map((corpus) => (
          <li key={corpus.id}>
            <button type="button" disabled={disabled} aria-haspopup="dialog"
              title={corpus.error || (corpus.total === null ? 'Meklē...' : `${corpus.total} atrasti šajā meklēšanā`)}
              onClick={() => onSelect(corpus.id)}>
              {corpus.label}<span>{corpus.total ?? (corpus.error ? '!' : '…')}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
