# Frontend

This is a small React and TypeScript interface for the suggestion review backend.
It uses Bootstrap 4.6.2 CSS, matching the Tēzaurs editor; `src/App.css` contains
the review layout and draft form styles. Interactions are handled by React.

Components are grouped under `src/components`: `review`, `tezaurs`, `draft`,
`usage`, `layout`, and `shared` (dialogs and common controls).
Draft fields are edited in popup forms; the overview shows only headings, lexemes
and definitions. Existing Tēzaurs entries are shown as a name and link; only newly added senses
are displayed and edited here when working with an existing entry.

## Run locally

Start PostgreSQL and the backend first. Then run:

```bash
cd frontend
npm install
npm run dev
```

Vite opens the frontend locally and proxies `/api` requests to
`http://localhost:8080`.
The development proxy supplies the test identity `local` automatically;
no login is needed when running `npm run dev`. Its settings are in `vite.config.ts`,
enabled only for the development server, excluding builds and preview.

The `/` page contains the reviewer interface. Deployment authentication is
handled by the reverse proxy, as described in the root README.

## Source structure

- `src/api/types.ts` describes data received from the backend.
- `src/api/client.ts` contains `fetch` calls to backend endpoints.
- `src/review/statuses.ts` maps backend enum values to Latvian labels.
- `src/pages/` contains complete pages.
- `src/components/` contains smaller parts used by a page.
- `src/App.tsx` selects the page for the current URL.
- `src/App.css` contains the small shared stylesheet.

The usual data flow is:

1. A page or component handles a user event.
2. It calls a function from `api/client.ts`.
3. The backend returns updated data.
4. React state is updated and React redraws the affected UI.

Useful checks before committing changes:

```bash
npm run lint
npm run build
```
