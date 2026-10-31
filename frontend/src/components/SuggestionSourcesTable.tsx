import { useState } from 'react'
import type { ReviewSource } from '../types'

const PAGE_SIZE = 5

type Props = {
  sources: ReviewSource[]
}

function AdditionalInformation({ source }: { source: ReviewSource }) {
  return (
    <>
      {source.source && <div><strong>Avots:</strong> {source.source}</div>}
      {source.flagInfo && <div><strong>Joma/stils:</strong> {source.flagInfo}</div>}
      {source.notes && <div><strong>Piezīmes:</strong> {source.notes}</div>}
      {source.contact && <div><strong>Saziņai:</strong> {source.contact}</div>}
    </>
  )
}

export function SuggestionSourcesTable({ sources }: Props) {
  const [page, setPage] = useState(0)
  const totalPages = Math.max(1, Math.ceil(sources.length / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages - 1)
  const visibleSources = sources.slice(
    currentPage * PAGE_SIZE,
    (currentPage + 1) * PAGE_SIZE,
  )

  return (
    <section className="suggestion-sources">
      <h3>{sources.length === 1 ? 'Iesniegtais ieteikums' : `Iesniegtie ieteikumi (${sources.length})`}</h3>
      <div className="suggestion-sources-table">
        <table>
          <thead>
            <tr>
              <th>Vārds</th>
              <th>Nozīme</th>
              <th>Piemērs</th>
              <th>Papildu informācija</th>
            </tr>
          </thead>
          <tbody>
            {visibleSources.map((source) => (
              <tr key={source.id}>
                <td>
                  <strong>{source.submittedTerm}</strong>
                  <small>{new Date(source.createdAt).toLocaleDateString('lv-LV')}</small>
                </td>
                <td>{source.submittedDefinition}</td>
                <td>{source.usageExample || '—'}</td>
                <td><AdditionalInformation source={source} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {totalPages > 1 && (
        <div className="suggestion-pagination">
          <button
            disabled={currentPage === 0}
            type="button"
            onClick={() => setPage(currentPage - 1)}
          >
            Iepriekšējie
          </button>
          <span>{currentPage + 1} / {totalPages}</span>
          <button
            disabled={currentPage + 1 === totalPages}
            type="button"
            onClick={() => setPage(currentPage + 1)}
          >
            Nākamie
          </button>
        </div>
      )}
    </section>
  )
}
