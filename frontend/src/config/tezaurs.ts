const TEZAURS_URL = 'https://tezaurs.lv'

export function tezaursEntryUrl(humanKey: string) {
  return `${TEZAURS_URL}/${encodeURIComponent(humanKey)}`
}
