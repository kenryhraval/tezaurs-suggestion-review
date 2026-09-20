import './App.css'
import { ImportPage } from './pages/ImportPage'
import { ReviewPage } from './pages/ReviewPage'

function App() {
  const path = window.location.pathname.replace(/\/$/, '')

  if (path === '/import') {
    return <ImportPage />
  }

  return <ReviewPage />
}

export default App
