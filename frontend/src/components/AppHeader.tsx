import { useEffect, useState } from 'react'
import { getSuggestionCounts } from '../api/client'
import headerImage from '../assets/tip-header.svg'
import searchIcon from '../assets/search.svg'

type Props = {
  reloadCount: number
  search: string
  onSearchChange: (value: string) => void
  onSearchSubmit: () => void
}

export function AppHeader({ reloadCount, search, onSearchChange, onSearchSubmit }: Props) {
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

  return <header className="app-header">
    <a className="app-brand" href="/" aria-label="TIP sākumlapa">
      <img src={headerImage} width="290" height="55" alt="TIP" />
    </a>
    <form className="header-search" role="search" onSubmit={(event) => {
      event.preventDefault()
      onSearchSubmit()
    }}>
      <label className="sr-only" htmlFor="suggestion-search">Meklēt ieteikumus</label>
      <input id="suggestion-search" type="search" placeholder="Vārds vai nozīme"
        value={search} onChange={(event) => onSearchChange(event.target.value)} />
      <button type="submit" aria-label="Meklēt" title="Meklēt">
        <img src={searchIcon} width="40" height="40" alt="" />
      </button>
    </form>
    <div className="app-header-info">
      <h1>Tēzaura ieteikumu perēklis</h1>
      {counts && <>
        <p>{counts.suggestions.toLocaleString('lv-LV')} ieteikumi</p>
        <p>{counts.cases.toLocaleString('lv-LV')} perēkļi</p>
      </>}
    </div>
  </header>
}
