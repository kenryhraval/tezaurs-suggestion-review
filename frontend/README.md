# Frontend

This is a small React and TypeScript interface for the suggestion review backend.

## Run locally

Start PostgreSQL and the backend first. Then run:

```bash
cd frontend
npm install
npm run dev
```

Vite opens the frontend locally and proxies `/api` requests to
`http://localhost:8080`.

## Pages

- `/` contains the reviewer interface.
- `/import` contains the temporary manual import form. It is deliberately not
  linked from the reviewer interface.

The import page is hidden from navigation, but it is not protected. Real access
control must be implemented in the backend before this application is public.

## Source structure

- `src/types.ts` describes data received from the backend.
- `src/api.ts` contains `fetch` calls to backend endpoints.
- `src/labels.ts` maps backend enum values to Latvian labels.
- `src/pages/` contains complete pages.
- `src/components/` contains smaller parts used by a page.
- `src/App.tsx` selects the page for the current URL.
- `src/App.css` contains the small shared stylesheet.

The usual data flow is:

1. A page or component handles a user event.
2. It calls a function from `api.ts`.
3. The backend returns updated data.
4. React state is updated and React redraws the affected UI.

Useful checks before committing changes:

```bash
npm run lint
npm run build
```
