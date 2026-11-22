import { useLoaderData, useRouteLoaderData, useNavigate, useOutletContext } from 'react-router'
import type { CurrentUser, Review, ReviewStatus } from '../api/types'
import { changeReviewStatus, mergeReviews } from '../api/client'
import { ReviewDetails } from '../components/review/ReviewDetails'
import { reviewPath } from '../review/links'

export type ReviewPageContext = {
  onReviewUpdated: () => void
}

export function ReviewPage() {
  const review = useLoaderData<Review>()
  const currentUser = useRouteLoaderData<CurrentUser>('root')
  const context = useOutletContext<ReviewPageContext>()
  const navigate = useNavigate()

  async function changeStatus(status: ReviewStatus): Promise<Review> {
    const updated = await changeReviewStatus(review.id, status)
    context.onReviewUpdated()
    return updated
  }

  async function linkDuplicate(sourceReviewId: string, targetReviewId: string) {
    const target = await mergeReviews(sourceReviewId, targetReviewId)
    context.onReviewUpdated()
    navigate(reviewPath(target.publicId), { replace: true })
  }

  return <ReviewDetails key={review.id} initialReview={review}
    username={currentUser?.username ?? null} onStatusChange={changeStatus}
    onReviewUpdated={context.onReviewUpdated} onDuplicate={linkDuplicate} />
}
