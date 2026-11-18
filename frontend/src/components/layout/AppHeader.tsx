import { useEffect, useState, type MouseEvent } from 'react'
import { Link } from 'react-router'
import { getSuggestionCounts } from '../../api/client'
import headerImage from '../../assets/tip-header.svg'
import { HeaderSearch } from './HeaderSearch'

type Props = {
  reloadCount: number
  query: string
  onSearch: (query: string) => void
  onHome: () => void
}

export function AppHeader({ reloadCount, query, onSearch, onHome }: Props) {
  const [searchResetCount, setSearchResetCount] = useState(0)
  const [counts, setCounts] = useState<{ suggestions: number; cases: number } | null>(null)

  useEffect(() => {
    let requestIsOutdated = false

    async function loadCounts() {
      try {
        const result = await getSuggestionCounts()
        if (!requestIsOutdated) {
          setCounts(result)
        }
      } catch {
        return
      }
    }

    loadCounts()
    return () => { requestIsOutdated = true }
  }, [reloadCount])

  function openHome(event: MouseEvent<HTMLAnchorElement>) {
    if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) {
      return
    }
    onHome()
    setSearchResetCount((current) => current + 1)
  }

  return <header className="app-header">
    <Link className="app-brand" to="/" onClick={openHome} aria-label="TIP sākumlapa">
      <img src={headerImage} width="290" height="55" alt="TIP" />
    </Link>
    <HeaderSearch key={searchResetCount} query={query} onSearch={onSearch} />
    <div className="app-header-info">
      <h1>Tēzaura ieteikumu perēklis</h1>
      {counts && <>
        <p>{counts.suggestions.toLocaleString('lv-LV')} ieteikumi</p>
        <p>{counts.cases.toLocaleString('lv-LV')} perēkļi</p>
      </>}
    </div>
  </header>
}
