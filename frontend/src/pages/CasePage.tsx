import { useLoaderData, useRouteLoaderData, useNavigate, useOutletContext } from 'react-router'
import type { CurrentUser, Review, ReviewStatus } from '../api/types'
import { changeReviewStatus, mergeReviewCases } from '../api/client'
import { ReviewDetails } from '../components/review/ReviewDetails'
import { casePath } from '../review/links'

export type CasePageContext = {
  onReviewUpdated: () => void
}

export function CasePage() {
  const review = useLoaderData<Review>()
  const currentUser = useRouteLoaderData<CurrentUser>('root')
  const context = useOutletContext<CasePageContext>()
  const navigate = useNavigate()

  async function changeStatus(status: ReviewStatus): Promise<Review> {
    const updated = await changeReviewStatus(review.id, status)
    context.onReviewUpdated()
    return updated
  }

  async function linkDuplicate(sourceCaseId: string, targetCaseId: string) {
    const target = await mergeReviewCases(sourceCaseId, targetCaseId)
    context.onReviewUpdated()
    navigate(casePath(target.publicId), { replace: true })
  }

  return <ReviewDetails key={review.id} initialReview={review}
    username={currentUser?.username ?? null} onStatusChange={changeStatus}
    onReviewUpdated={context.onReviewUpdated} onDuplicate={linkDuplicate} />
}
