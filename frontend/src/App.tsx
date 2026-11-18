import './App.css'
import { createBrowserRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { loadCurrentUser } from './auth/currentUserLoader'
import { CaseLoading } from './components/review/CaseLoading'
import { CaseErrorPage } from './pages/CaseErrorPage'
import { CasePage } from './pages/CasePage'
import { MainPage } from './pages/MainPage'
import { loadCase, rejectUnknownCasePath } from './review/caseLoader'

const router = createBrowserRouter([
  {
    id: 'root',
    path: '/',
    loader: loadCurrentUser,
    Component: MainPage,
    HydrateFallback: () => null,
    children: [
      {
        id: 'case',
        path: ':publicId',
        loader: loadCase,
        Component: CasePage,
        ErrorBoundary: CaseErrorPage,
        HydrateFallback: CaseLoading,
      },
      {
        path: '*',
        loader: rejectUnknownCasePath,
        ErrorBoundary: CaseErrorPage,
      },
    ],
  },
])

function App() {
  return <RouterProvider router={router} />
}

export default App
