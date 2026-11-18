import type { LoaderFunctionArgs } from 'react-router'
import type { CurrentUser } from '../api/types'
import { getCurrentUser } from '../api/client'

export async function loadCurrentUser({ request }: LoaderFunctionArgs): Promise<CurrentUser> {
  try {
    return await getCurrentUser(request.signal)
  } catch {
    return { username: null }
  }
}
