import { isRouteErrorResponse, Link, useRouteError } from 'react-router'

export function ReviewErrorPage() {
  const routeError = useRouteError()
  let errorMessage = 'Neizdevās atvērt perēkli.'

  if (isRouteErrorResponse(routeError) && typeof routeError.data === 'string') {
    errorMessage = routeError.data
  } else if (routeError instanceof Error) {
    errorMessage = routeError.message
  }

  return (
    <section className="details">
      <p className="error" role="alert">{errorMessage}</p>
      <Link to="/">Atpakaļ uz ieteikumiem</Link>
    </section>
  )
}
