# sv

Everything you need to build a Svelte project, powered by [`sv`](https://github.com/sveltejs/cli).

## Creating a project

If you're seeing this, you've probably already done this step. Congrats!

```sh
# create a new project
npx sv create my-app
```

To recreate this project with the same configuration:

```sh
# recreate this project
npx sv@1.4.0 create --template minimal --types ts --add prettier eslint --install npm web
```

## Adding features

Add features to your project with `sv add`:

```sh
npx sv add
```

For example, to add Tailwind CSS:

```sh
npx sv add tailwindcss
```

## Developing

Once you've created a project and installed dependencies with `npm install` (or `pnpm install` or `yarn`), start a development server:

```sh
npm run dev

# or start the server and open the app in a new browser tab
npm run dev -- --open
```

## Building

To create a production version of your app:

```sh
npm run build
```

You can preview the production build with `npm run preview`.

> To deploy your app, you may need to install an [adapter](https://svelte.dev/docs/kit/adapters) for your target environment.

## Reports

The report pages download files from the SPR services the same way the SPR `WebController` does: the same URLs,
`Authorization: Basic base64(username:session)`, the same validations, error messages and file names.

| Report          | Form                | Upstream                                                                                |
| --------------- | ------------------- | --------------------------------------------------------------------------------------- |
| Общий отчет     | month               | `GET {REPORT_URL}/api/export/excel?from&to` (first/last day of the month; up to 10 min) |
| Отчет по ИНН    | ИНН, 14 digits      | `POST {SPR_API_URL}/spr/api/v1/file/all-by-inn?inn=` → `SPR-{inn}.zip`                  |
| Получить файл   | scoringId           | `GET {SPR_API_URL}/spr/api/v1/file/{scoringId}`                                         |
| Архив за период | from, to (≤ 3 days) | `GET {SPR_API_URL}/spr/api/v1/file/all?from&to` → zip                                   |
| Hard Collection | —                   | not connected yet (`WebController` only renders its page)                               |

To move them to another project, take:

- `src/lib/reports.ts`: the reports, their fields and the checks (shared by the form and the server)
- `src/lib/server/reports.ts`: the upstream call of each report and its error messages
- `src/routes/(app)/reports/[report]/download/+server.ts`: the endpoint the form posts to; streams the file back
- `src/lib/download.ts`: saves the answer as a file in the browser
- `src/routes/(app)/reports/[report]/+page.svelte`: the form

Environment: `SPR_API_URL` (`server.base.url`, default `http://localhost:8060`) and `REPORT_URL` (`report.url`, default
`http://localhost:8064`).
