import { useEffect, useState, type FormEvent } from 'react'
import searchIcon from '../../assets/search.svg'

type Props = {
  query: string
  onSearch: (query: string) => void
}

export function HeaderSearch({ query, onSearch }: Props) {
  const [search, setSearch] = useState(query)

  useEffect(() => {
    const nextQuery = search.trim()
    if (nextQuery === query) return
    const timeout = window.setTimeout(() => onSearch(nextQuery), 300)
    return () => window.clearTimeout(timeout)
  }, [search, query, onSearch])

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextQuery = search.trim()
    if (nextQuery !== query) onSearch(nextQuery)
  }

  return <form className="header-search" role="search" onSubmit={submitSearch}>
    <label className="sr-only" htmlFor="suggestion-search">Meklēt ieteikumus</label>
    <input id="suggestion-search" type="search" placeholder="Vārds vai nozīme"
      value={search} onChange={(event) => setSearch(event.target.value)} />
    <button type="submit" aria-label="Meklēt" title="Meklēt">
      <img src={searchIcon} width="40" height="40" alt="" />
    </button>
  </form>
}
