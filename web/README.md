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

## Login and reports

The console talks to one API, the Spring app at `API_URL`. Login and the reports are the SPR `WebController` turned
into a REST API (`core-console`, package `kg.bakai.console.web`): no server session and no redirects, every call sends
`Authorization: Basic base64(username:session)` and errors come back as a ProblemDetail whose `detail` is the
message to show.

| Endpoint                                                 | Does                              | SPR behind it                                              |
| -------------------------------------------------------- | --------------------------------- | ---------------------------------------------------------- |
| `POST /api/v1/console/auth/login` `{username, password}` | `{username, session}`             | `AuthService`                                              |
| `GET /api/v1/console/reports/files/{scoringId}`          | the file of a check               | `GET {server.base.url}/spr/api/v1/file/{scoringId}`        |
| `GET /api/v1/console/reports/files?from&to`              | zip of a period (≤ 3 days)        | `GET {server.base.url}/spr/api/v1/file/all?from&to`        |
| `GET /api/v1/console/reports/general?month=2026-11`      | Excel of a month                  | `GET {report.url}/api/export/excel?from&to` (up to 10 min) |
| `GET /api/v1/console/reports/inn/{inn}`                  | zip of a client (`SPR-{inn}.zip`) | `POST {server.base.url}/spr/api/v1/file/all-by-inn?inn=`   |

Hard Collection is not connected yet: the `WebController` only renders its page.

Moving it to SPR: copy `ConsoleAuthController`, `ReportController` and `SprReportClient`, use SPR's `AuthService` and
`UsernameSessionResponse` instead of `kg.bakai.console.auth` (same method and record), and keep `server.base.url` and
`report.url`. In the console, the files involved are `src/lib/server/api.ts` (login, Basic header),
`src/lib/server/reports.ts`, `src/routes/(app)/reports/[report]/download/+server.ts`, `src/lib/download.ts`,
`src/lib/reports.ts` and the report page.
