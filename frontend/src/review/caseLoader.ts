import { replace, type LoaderFunctionArgs } from 'react-router'
import { getReviewByPublicId } from '../api/client'
import { casePath, parsePublicId } from './links'

export async function loadCase({ request, params }: LoaderFunctionArgs) {
  const publicId = parsePublicId(params.publicId)
  if (publicId === null) {
    throw new Response('Nederīga perēkļa saite.', { status: 400 })
  }

  const requestedPath = new URL(request.url).pathname
  const canonicalPath = casePath(publicId)
  if (requestedPath !== canonicalPath) {
    return replace(canonicalPath)
  }

  return getReviewByPublicId(publicId, request.signal)
}

export function rejectUnknownCasePath() {
  throw new Response('Nederīga perēkļa saite.', { status: 404 })
}
