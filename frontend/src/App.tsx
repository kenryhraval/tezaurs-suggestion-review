import './App.css'
import { createBrowserRouter } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { loadCurrentUser } from './auth/currentUserLoader'
import { ReviewLoading } from './components/review/ReviewLoading'
import { ReviewErrorPage } from './pages/ReviewErrorPage'
import { ReviewPage } from './pages/ReviewPage'
import { MainPage } from './pages/MainPage'
import { loadReview, rejectUnknownReviewPath } from './review/reviewLoader'

const router = createBrowserRouter([
  {
    id: 'root',
    path: '/',
    loader: loadCurrentUser,
    Component: MainPage,
    HydrateFallback: () => null,
    children: [
      {
        id: 'review',
        path: ':publicId',
        loader: loadReview,
        Component: ReviewPage,
        ErrorBoundary: ReviewErrorPage,
        HydrateFallback: ReviewLoading,
      },
      {
        path: '*',
        loader: rejectUnknownReviewPath,
        ErrorBoundary: ReviewErrorPage,
      },
    ],
  },
])

function App() {
  return <RouterProvider router={router} />
}

export default App
