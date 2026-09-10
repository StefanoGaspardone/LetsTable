# Let's Table (TODO finish and update this README)

A personal board-game tracking app for a private group of friends - collection, wishlists, play sessions, and friend management - built with Spring Boot / Kotlin on the backend and React Native / Expo on the frontend.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
  - [Stack](#stack)
  - [Authentication model](#authentication-model)
  - [BoardGameGeek integration](#boardgamegeek-integration)
  - [Match lifecycle](#match-lifecycle)
- [How to Run](#how-to-run)
  - [Dependencies](#dependencies)
  - [Run the infrastructure containers](#run-the-infrastructure-containers)
  - [Environment variables](#environment-variables)
  - [Run the backend](#run-the-backend)
  - [Run the mobile app](#run-the-mobile-app)
- [Endpoints](#endpoints)
- [Frontend](#frontend)
  - [Stack](#stack-1)
  - [Design system](#design-system)
  - [Navigation structure](#navigation-structure)
  - [Notable screens](#notable-screens)
- [Health monitoring](#health-monitoring)
- [Tests](#tests)
- [Notable bugs found and fixed](#notable-bugs-found-and-fixed)
- [Backlog](#backlog)

## Overview

Let's Table is a board game companion app for a small, private friend group - the kind of thing a BoardGameGeek power user and their regular game night crew would actually use day to day: track who owns what, what everyone wants next, who's won the most, and what you played last Tuesday. It is not a general-audience product; it is built and seeded for a known, closed set of users (friends, added via friend requests, not public discovery).

The project is split into:

- **Backend**: a single Spring Boot / Kotlin monolith, backed by PostgreSQL and MinIO
- **Frontend**: a React Native / Expo mobile app (Android/iOS), the only client - there is no companion web app

## Architecture

Let's Table is a single-service monolith - the scale (a handful of users) doesn't justify splitting bounded contexts across services, and a monolith keeps local development to "start one process, start one app."

### Stack

- **Backend**: Spring Boot 4 / Kotlin, PostgreSQL (Flyway migrations), MinIO for object storage (uploaded rulebook PDFs, user avatars), stateless JWT auth
- **Frontend**: React Native (Expo SDK 57), NativeWind v4 (Tailwind-style styling), Expo Router, TanStack Query, Axios.

### Authentication model

- **Access tokens**: short-lived JWTs (15 minutes).
- **Refresh tokens**: opaque random strings, hashed (SHA-256) and persisted server-side, rotated on every use (old token revoked, new one issued). The mobile app's Axios client queues concurrent requests that hit a `401` while a refresh is already in flight, so a burst of simultaneous calls triggers exactly one refresh, not one per request.
- **Email verification / password reset**: OTP-based, with a bounded attempt counter and a resend cooldown, mirroring the same shape for both flows.
- Every controller is annotated `@PreAuthorize("hasRole('USER')")` — there is no separate admin role; every account has the same permissions over its own data.

### BoardGameGeek integration

Games are not manually entered — they're pulled from the public BGG XML API on first reference (search, or adding a game not yet cached) and cached locally:

- A background scheduler refreshes BGG's "hot games" list periodically, so the app always has a ready-made browse list without a live API call on every request.
- Game detail sync (name, image, player count, playtime, description, designers/artists/publishers, expansions) happens on demand, with a staleness check — a cached game older than the configured TTL is re-synced transparently the next time it's requested.
- Game descriptions from BGG often end with a trailing editorial note ("—description from the publisher", "—description from the designer", etc.); this is stripped during sync via a small regex pass, along with normalizing BGG's raw HTML (`<br>` tags, entity encoding) into plain text.
- Cover images and uploaded rulebook PDFs are stored in MinIO, not the database — `Game`/`GameRuleFile` hold only a reference.

### Match lifecycle

A match can be **in progress** or **completed** — this is a derived state (`durationMinutes == null` means in progress), not a stored enum:

- Registering a match creates it immediately in progress, with players/teams and starting colors chosen up front, but no scores or winner yet.
- "Terminate match" is a separate step: it collects final scores, winner(s) (supports ties — more than one entry can be marked winner), and which player/team went first, then computes `durationMinutes` server-side from `now - createdAt` — the client never sends a duration directly.
- Matches support two shapes: **individual** (a flat list of `MatchPlayer`s, each with an optional linked `User` or a free-text guest name) or **team-based** (`MatchTeam`s, each owning a subset of `MatchPlayer`s) — a match is one or the other, never both, enforced at the request-validation level.
- A player slot can be a registered friend or a **guest** (name only, no account) — useful for game nights that include people who aren't in the app.

## How to Run

### Dependencies

- JDK 21+
- Gradle (wrapper included)
- Node.js 20+ and npm (for the mobile app)
- Docker and Docker Compose
- Expo Go (or an Android/iOS simulator) to run the mobile app

### Run the infrastructure containers

From the repository root:

```bash
docker compose up -d
```

This starts:

- **PostgreSQL** — the app's single database
- **MinIO** — object storage for game cover images, uploaded rulebook PDFs, and user avatars

The backend itself is **not** containerized during development — it runs directly from the IDE (`./gradlew bootRun`) for hot reload and debugging. A multi-stage Dockerfile exists for production builds (Gradle build stage → slim `eclipse-temurin` JRE runtime stage), with a `.dockerignore` alongside it in `backend/`.

### Environment variables

The backend seeds demo data on first startup when `seeding.enabled=true` (used in dev; disabled in the test profile). Notable configuration:

```.env
JWT_SECRET=...                      # HS256 signing secret
JWT_ACCESS_TOKEN_TTL_MINUTES=15
JWT_REFRESH_TOKEN_TTL_DAYS=7
MINIO_URL=http://localhost:9000
MINIO_ACCESS_KEY=...
MINIO_SECRET_KEY=...
MINIO_BUCKET=...
```

### Run the backend

```bash
cd backend
./gradlew bootRun
```

Swagger UI: `http://localhost:8080/swagger-ui.html`.

### Run the mobile app

```bash
cd frontend-mobile
npm install
npx expo start
```

Scan the QR code with Expo Go, or launch an Android/iOS simulator from the Expo CLI menu.

## Endpoints

Grouped by resource; full detail in Swagger.

| Resource | Notable endpoints |
|---|---|
| Auth | `POST /auth/signup`, `/activate`, `/login`, `/refresh`, `/logout`, `/forgot-password`, `/reset-password` |
| Games | `GET /games/search`, `/games/hot`, `/games/{bggId}`, `/games/{bggId}/expansions`; rule files under `/games/{id}/rules` |
| Collection | `GET/POST /collection`, `GET /collection/status/{gameId}` |
| Wishlists | `GET /wishlists` (mine), `GET/POST/PATCH/DELETE /wishlists/{id}`, item and member management |
| Matches | `POST /matches`, `GET /matches` (filterable, paginated), `GET /matches/{id}`, `PATCH /matches/{id}` (also used to "finish" a match), `DELETE /matches/{id}`, `GET /matches/calendar`, `GET /matches/recent-games`, `GET /matches/win-stats` |
| Friends | send/accept/reject/cancel requests, list friends/received/sent, remove friend |
| Users | search, profile, delete account (anonymizing, with cascading cleanup of solo matches and refresh tokens) |
| Push tokens | register/unregister device push tokens |
| Health | `GET /health` — unauthenticated, polled by the mobile app (see [Health monitoring](#health-monitoring)) |

`GET /matches/recent-games` returns the distinct games played across a user's last 10 matches, most recent first, unpaginated — used to surface "recent games" at the top of the game picker instead of always showing the full collection first.

`GET /matches/win-stats` returns total completed matches and total wins for the current user, counting both individual wins (`MatchPlayer.isWinner`) and team wins where the user was a member of the winning team — used for the win-rate card on the home screen.

## Frontend

### Stack

Expo Router (file-based navigation, `(tabs)` group for the five bottom-tab root screens plus nested detail routes), NativeWind v4, TanStack Query for all server state (no separate client-state store), `react-native-reanimated` for the collapsing game-detail header, the animated pill on the custom tab bar, and the staggered FAB menu.

### Design system

Warm cream/terracotta palette (`#C45135` as the primary accent), `Playfair Display` for screen titles and section headers, `Plus Jakarta Sans` for body/UI text. Light theme only.

### Navigation structure

```
app/
├── (tabs)/
│   ├── home.tsx
│   ├── collection.tsx
│   ├── matches.tsx
│   ├── friends.tsx
│   ├── profile.tsx
│   ├── game/[bggId]/index.tsx
│   ├── match/[id]/index.tsx
│   ├── match/[id]/finish.tsx
│   ├── match/[id]/edit.tsx
│   ├── my-wishlists.tsx
│   └── wishlist/[id].tsx
├── (auth)/welcome, login, signup, activate
├── browse (modal)
└── +not-found.tsx
```

Detail routes (`game/[bggId]`, `match/[id]`) live **inside** the `(tabs)` group rather than as siblings outside it, with `options={{ href: null }}` on their `<Tabs.Screen>` entries — this keeps the custom animated tab bar visible while browsing a game or match's detail page, rather than hiding it the moment the user navigates one level deep, which is the more common pattern but felt disorienting for an app this shallow.

A shared `useRefetchOnFocus(queryKey)` hook invalidates a TanStack Query key by prefix every time a screen regains focus (`expo-router`'s `useFocusEffect`), so returning to a list after creating/editing something elsewhere always shows fresh data without every screen re-implementing its own invalidation logic.

### Notable screens

- **Home**: a win-rate card (a horizontal bar showing wins as a fraction of total completed matches, hidden entirely rather than showing "0/0" when the user has no matches yet), quick-stat cards for collection size and friend count, the latest match as a large highlight card followed by up to four more recent matches as compact cards, and a horizontally-condensed wishlist section — each section has a "View all" link that only appears once there's something to view.
- **Game detail**: a collapsing hero image (BGG's cover art) behind a segmented Info/File/Expansions tab pager, with a sticky tab bar that fades in only once the inline one scrolls out from under the header — timed off a measured layout position, not a fixed scroll offset, so it stays correct regardless of how much content (base-game badge, credits, sleeve info) sits above the tabs for a given game.
- **Match detail**: a podium (1st/2nd/3rd) for completed matches, individual players showing avatar + name, team-based matches showing a colored initial-letter avatar per team with a tap-to-open bottom sheet listing that team's actual members in a small grid.
- **Match finish flow**: one row per player/team, with a tap-to-star control for who started, a score field, and a winner toggle supporting ties; the submit button is disabled until every row has a score, a starting player is chosen, and at least one winner is marked.
- **Player/guest picker**: search results and already-selected identities render as the same avatar-and-name grid card (registered users and free-text guests alike), so adding people to a match feels the same regardless of whether they have an account.

## Health monitoring

The mobile app polls `GET /health` every 15 seconds (plus once immediately on launch) from the root layout, independent of whatever screen is active. If a check fails (timeout or non-2xx), a full-screen overlay appears — "Qualcosa è andato storto, verifica che il server sia attivo" with a manual retry button — blocking interaction until a health check succeeds again, either from the next automatic poll or the retry button. The polling loop itself keeps running underneath the overlay, so the app recovers on its own the moment the backend comes back, without requiring the user to tap anything.

## Tests

```bash
cd backend
./gradlew test
```

- **Unit** (MockK/Mockito) — services, BGG XML client (against `mockwebserver3`), schedulers.
- **Integration** (Testcontainers: Postgres + MinIO, singleton container pattern shared across the test run) — full controller-through-repository coverage for auth, games, collection, matches, friends, wishlists, push tokens, and account deletion.

No frontend automated test suite yet (manual testing against the real backend during development) — see [Backlog](#backlog).

## Notable bugs found and fixed

- **`LazyInitializationException` on friend request lists.** `FriendRequestRepository`'s finder methods returned `FriendRequest` entities with `sender`/`receiver` left lazy; serializing them to a DTO outside the transaction threw. Fixed by adding `JOIN FETCH fr.sender JOIN FETCH fr.receiver` to every finder used by a response-returning endpoint.
- **`TransientPropertyValueException` on account deletion.** `UserService.deleteAccount` deleted a user's solo matches by cascading through `Match`, but `MatchPlayer` rows referencing that match weren't deleted first — Hibernate tried to null out a foreign key on an already-removed parent. Fixed by explicitly deleting the match's `MatchPlayer` rows before deleting the `Match` itself.
- **Sort parameter using the wrong separator.** `resolveSort` expected `field-direction` (e.g. `playedAt-desc`), but several call sites were built with `field,direction` (a comma), silently falling through to a default sort instead of erroring — easy to miss since nothing looked broken, results were just never actually sorted as requested.
- **Tab bar indicator animating through hidden tabs.** The custom animated pill under the active tab computed its position from `state.index` against the *full* route list — once detail routes (`game/[bggId]`, `match/[id]`) were added to the same `(tabs)` navigator with `href: null` to keep the tab bar visible on them, the full route list no longer matched the five visible icons, so the indicator slid to the wrong position (or off-screen) whenever a hidden route was focused. Fixed by computing the active index against a list filtered to only the icon-mapped routes, and freezing the indicator's last known valid position (rather than clamping to index 0) while fading out on a route with no icon, so it dissolves in place instead of visibly sliding across the bar.
- **`onLayout`-driven tab-pager height overwritten by the wrong tab.** The game-detail screen's horizontal Info/File/Expansions pager used a single `pageHeight` state updated by each tab's `onLayout`, guarded by `if (activeTab === key)` — but `onLayout` only re-fires when a tab's *own* layout actually changes, not every time it becomes active again. Switching away and back to a tab whose content hadn't changed left the shared height stuck at whatever the *previously* active tab had last reported, clipping content. Fixed by keeping one height per tab key in a small record instead of a single shared value, so each tab's height is remembered independently of which one is currently visible.
- **`useEffect` never re-opening a bottom sheet for the same selection.** A team-detail bottom sheet was opened via `useEffect(() => { if (selectedTeam) sheetRef.current?.present() }, [selectedTeam])` — tapping the *same* team twice in a row passed the identical object reference to `setSelectedTeam`, so React saw no state change and the effect never re-ran, leaving the sheet closed on the second tap. Fixed by calling `.present()` directly inside each row's `onPress` alongside `setSelectedTeam`, rather than relying on an effect keyed to a value that isn't guaranteed to change.
- **Team win/loss counting swapped between the two match modes.** A stats query meant to branch on `match.isTeamBased` had its two branches accidentally reading from the wrong relation — the team-based branch queried `match.players` (always empty for a team match) and the individual branch queried `match.teams` (always empty for an individual match), so win/loss totals came back as zero for every match regardless of mode. Caught immediately once actual data was checked against the raw seeded rows, since a match known to have a winner still reported `0/0`.

## Backlog

| Item | Notes |
|---|---|
| Friends tab | Currently a "coming soon" placeholder; friend request send/accept/list is implemented backend-side but not yet wired into the mobile UI. |
| Profile tab | Same — placeholder screen; account settings, logout, and profile editing exist as backend endpoints but no frontend yet. |
| Wishlist detail & picker | `my-wishlists` and `wishlist/[id]` are placeholder screens; wishlist CRUD, member management, and item add/remove are implemented backend-side. |
| Push notifications | Backend has a `push_tokens` table and register/unregister endpoints; no notification sending (friend request received, match result, etc.) wired up yet. |
| Meeple avatars | Replace the flat color-dot next to each player (match detail, match-finish screen) with a small meeple icon rendered in the player's chosen color. |
| Multi-touch "finger picker" for random turn order | Players each place a finger on screen; after a touch-timeout, a countdown picks a random starting player/team, turn order proceeding clockwise from touch position. Not started. |
| Match expansions used | Allow tagging which expansion(s) of a game were used in a given match, alongside the base game — requires both backend (a join table) and frontend (an extra step in the register-match flow) changes. |
| Component/sleeve info on game detail | Investigating a data source for sleeve dimensions/card counts per component (BGG has no public, documented endpoint for this); candidate is scraping sleeveyourgames.com, pending a reply to an email asking about an official API. |
| EAS build / distribution | No production build pipeline yet — the app is currently only run through Expo Go / dev builds during development. |
| Automated frontend tests | No test suite yet for the mobile app; testing has been manual, against the real running backend, throughout development. |