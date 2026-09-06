# Bulk AI tools

The bulk entry/updating editor includes an **AI tools** frame that works on the whole deck draft at once. It can generate additional cards, correct facts, enhance explanations, suggest additive tags, review duplicates for merging, and identify underrepresented topics.

These tools use the same AI configuration as the rest of the app (an OpenAI-compatible endpoint, hosted or local). AI features are optional; without a configured endpoint the tools report a request failure. See [AI-Integration.md](AI-Integration.md) for setup.

## What the tools do

| Tool | Operation | Behavior |
|------|-----------|----------|
| Generate / Add Cards | `generate` | Adds the requested number of new, distinct cards (1–100) at the selected difficulty |
| Correct Deck | `correct` | Fixes factual inaccuracies in front/back text; preserves tags and card identity |
| Enhance Deck | `enhance` | Improves clarity, explanations, examples and Markdown formatting; preserves facts and difficulty |
| Find Duplicates | `duplicates` | Proposes groups of duplicate cards with merged content; you select which merges to stage |
| Suggest Tags | `tags` | Adds relevant tags, reusing existing deck tag spelling; never removes or renames tags |
| Fill Topic Gaps | `gaps` | Suggests underrepresented topics inferred from the deck name and cards |

Generate / Correct / Enhance are always visible. Find Duplicates, Suggest Tags, Fill Topic Gaps, the difficulty selector, and custom instructions are under **More tools & options**.

## How it works

- All actions use the **current draft**, including unsaved cards and cards hidden by search/tag filters. Deleted and blank rows are excluded.
- **Changes stay in the editor** until you press **Save All Changes**. AI operations never write to the database directly.
- Generation inserts new unsaved cards at the top of the grid.
- Status, elapsed time, and errors are reported inside the AI tools frame while the request runs.
- Closing the editor discards its draft and any pending results.

> **Note:** The deck name, draft cards, tags, and selected topics are sent to the configured AI provider with every request. Treat it like any other AI feature and review output before saving.

## Review, compare, and undo

- **Compare Changes** shows before/after content and tags for the most recent staged AI operation.
- **Undo Last AI Change** reverts only AI-owned values while preserving later manual edits. Conflicting fields are kept and reported. It is one level deep (no redo) and available until you save or close the editor.
- If the draft changes while a request is running, the result is discarded and you are asked to run the operation again.

## Duplicate merging

1. Run **Find Duplicates**. The AI proposes non-overlapping groups with a reason and merged content.
2. Review each group, select the ones you want, and press **Apply Selected Merges**.
3. The **oldest saved card** survives and keeps its SRS review history; the other members are marked for deletion. Review histories are **not** combined. Tags from all group members are combined on the survivor.

Saving the merge in one bulk save is transactional: a failed save rolls back updates, deletions, and review/audio cleanup together.

## Topic gaps

- **Fill Topic Gaps** only analyzes; selecting topics changes nothing by itself.
- Select suggested topics, then press **Generate Cards for Selected Topics**. The card count is the **total** shared across all selected topics.
- Suggestions become stale when the draft changes; rerun the analysis for fresh results.

## Limits and timeouts

| Limit | Value |
|-------|-------|
| Cards per request | 200 (draft rows with unique IDs) |
| Card text | 5,000 characters per side (front/back) |
| Custom instructions | 2,000 characters |
| Generated cards | 1–100 per request |
| Client-side abort | 12 minutes |
| Server stream timeout | 15 minutes (with keep-alive heartbeats every 30 seconds) |

Custom instructions refine language, audience, style, or formatting. They cannot override the operation's task, output schema, or additive-only tag behavior.

## API and validation

The backend exposes `POST /api/ai/deck-assist`, which requires authentication like the rest of the API. It responds with a **Server-Sent Events** stream: a `processing` comment, periodic heartbeats, then a `done` event with the validated JSON result or an `error` event with a safe, actionable message. Provider error bodies, credentials, and deck contents are never echoed back.

Each operation has a strict JSON schema (`cards`, `groups`, `tags`, or `topics`). Before anything reaches the editor, the backend:

- Rejects malformed or fenced output and reasoning-model channel markers
- Validates response shape, row identifiers against the submitted draft, uniqueness, and counts (for example, `correct`/`enhance` must return every submitted card exactly once)
- Enforces additive-only tags and rejects duplicate generated cards or repeated topic suggestions
- Rejects incomplete responses (finish reason other than `stop`)

If any check fails, the operation reports an error and **no changes are applied**. Hidden retries are disabled for these expensive calls.

See the source for details: `DeckAssistService.java`, `DeckAssistController.java`, and the frontend `BulkAiTools.tsx`.

## Testing the feature

Backend checks (from the repo root):

```bash
./mvnw -Dskip.npm -Dskip.installnodenpm -Dtest=DeckAssistServiceTests,DeckAssistControllerTests,BulkAiSaveTests test
```

Opt-in live smoke tests against a real endpoint (synthetic cards only; responses are written under `target/`):

```bash
./mvnw -Dtest=DeckAssistLiveTests -DdeckAssist.smoke=true \
    [-DdeckAssist.url=http://localhost:8087] [-DdeckAssist.model=...] [-DdeckAssist.operation=correct] test
```

For a hosted endpoint, supply `DECK_ASSIST_TEST_API_KEY` in the environment.

Frontend checks (from `dep/ai-forgot-this-frontend`):

```bash
npm run test:deck-assist   # draft changes, merge/undo, SSE parsing
npm run test:browser       # bulk entry workflow with mocked API responses
```

Browser tests need Chromium (`npx playwright install chromium`) or `PLAYWRIGHT_CHROMIUM_EXECUTABLE` pointing at an existing executable. They start their own Vite server and never call a model or database.