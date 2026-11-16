# Design notes

How the data in this app is identified, where learning progress lives, and
what is derived from what. Read this before changing the schema, the archive
format, or anything that schedules cards.

## Identity

Every record a person creates -- and might rename, move, reset, or carry to
another device -- has a **stable unique identifier**, `uid`:

| Record | Table | `uid` |
|---|---|---|
| Card | `cards` | yes |
| Card group | `groups` | yes |
| Learning group | `learning_groups` | yes |
| Opponent (a person) | `opponents` | yes |
| Learning concept | `concepts` | yes |
| Practice session | `practice_sessions` | yes |
| Review log | `review_logs` | yes |

Rules:

- **A `uid` is a random (version 4) UUID** in canonical lower-case text, made
  by `newUid()` when the record is created, and **never changes** -- not on
  rename, edit, reset, move between groups, or import. Each table has a
  unique index on it.
- **Row ids (`id`) are local.** They are this database's own, are what foreign
  keys use, and mean nothing on another device. Never put one in an export or
  use one to match records across devices.
- **Names are labels.** A name (or a card's question) is what a person reads
  and may change at any time. Nothing should depend on a name staying the
  same. Where a name is stored for history (`review_logs.groupName`), it is a
  snapshot for display; the identity is alongside it (`review_logs.groupUid`).
- **Join records have no `uid` of their own.** Card-group and card-concept
  links, and per-learning-group states (`card_learning_state`,
  `concept_learning_state`), are identified by the records they join.
- **New references use the `uid`.** Anything that records "which group /
  card / session" beyond a local foreign key -- history, exports -- records
  the `uid`, and may keep a name next to it for people and for older readers.

Existing rows got a fresh `uid` in the 17 -> 18 migration. Review logs got
their `groupUid` from the oldest group with the name they recorded, the best
that could be done once names were all there was; a log naming a group that
no longer exists has none.

## Learning contexts

Learning progress is kept per **learning context**:

- **Global** -- every card's own progress, on the card itself. Card groups in
  no learning group practise against it.
- **A learning group** -- a separate set of progress, one row per card in
  `card_learning_state`. Every card group in it shares that progress.

How a session is drawn and scheduled (cards per session, shuffling, practice
days, maximum interval, retention, fuzzing) belongs to the learning context:
the learning group's settings, or the app-wide ones for Global.

A **review** belongs to the context of the card group it was made in, *as the
group is now*: the log's `groupUid` finds the group, and the group's current
learning group is the context. No group (all-cards practice, the card editor
without a group) is Global. A review whose group no longer exists belongs to
no context. Because the group is found by `uid`, renaming a group keeps its
history where it was; moving a group to another learning group moves its
history with it.

## Learning concepts

Concepts are global tags on cards (`card_concept_cross_ref`). A concept's
learning state is **derived, never accumulated**: it is the replay of the
review history of the cards that carry it, oldest first, separately for each
learning context.

- A practice session is **one** concept review, graded with the rounded mean
  of its cards' grades; skips are left out. A review outside a session is a
  review of its own.
- The replay uses each review's own group's scheduling settings, with
  interval fuzzing off, so rederiving always gives the same state.
- A concept whose cards have no counted history is new.
- The result is stored -- Global on the concept, one `concept_learning_state`
  row per learning group -- only so drawing a session needs no replay.

It is rederived whenever its inputs change: a card is tagged or untagged, a
session is graded, a card is graded in the editor, a card is deleted or reset,
history is imported, or a group's learning group, name or existence changes.

### Tagging and availability

Cards are tagged with concepts from the **card editor**; the Concepts screen
defines concepts and where they are available, and shows how many cards use
each, but does not tag cards.

A concept is **available** in learning contexts: the learning groups it is
linked to (`concept_learning_group`) and Global when `concepts.inGlobal`. The
card editor offers a concept for a card only when it is available in one of
the card's learning contexts -- its groups' learning groups, or Global for a
group in none and for a card in no group (`offeredConceptIds`).

**Tagging is always allowed**, whether or not a context schedules by concept.
Where concept scheduling is off, the app says so instead of preventing
anything: the concept dialog marks those contexts, the concept list marks
them "(scheduling off)", and the card editor notes that the card is scheduled
card by card there. The tags are kept and take effect when the switch is
turned on. Availability limits what can be *added*: a card already
tagged with a concept that is not offered keeps it, shown so it can be
removed. "Create New Concept" in the card editor makes the concept available
in all of the card's learning contexts.

Scheduling *by* concept is opt-in per context: each learning group has a
`useConcepts` switch and Global has a setting. Where it is on, a session draws
the due concepts and shows the cards covering the most of them; elsewhere,
cards are scheduled one by one. The switch never changes what is derived.

## Resets

Resetting progress does not delete history. It records **when**:

- `cards.lastReset` -- the card's Global progress was last reset.
- `card_learning_state.lastReset` -- its progress in that learning group was.
  A full reset stamps every learning group, whether the card held a state
  there or not.

Anything derived from history counts **only reviews after the reset** in that
context. The reviews before it stay in the review log and on the History
screen; they are history, not progress. A reset time of 0 means never.

## History

History is the practice sessions (`practice_sessions`) and the review logs
(`review_logs`). Every entry has its own `uid`, and every review records what
it refers to so that it stays attributable through renames, deletions and
trips between devices:

| A review refers to | Locally | By identifier |
|---|---|---|
| its card | `cardId` (deleting a card deletes its reviews) | `cardUid` in exports |
| its session | `sessionId` | `sessionUid` in exports |
| its card group | -- | `groupUid` (plus `groupName`, the name at the time, for display) |
| its opponent | `opponentId`, a soft reference | `opponentUid` |

The opponent reference is soft on purpose: deleting an opponent leaves their
reviews in the history. Those reviews keep `opponentUid`, so when the
opponent comes back -- by import, under any local id or name -- the reviews
are theirs again: after every import, reviews missing an `opponentUid` take
the one of their local opponent, and every identified review is pointed at
whichever local opponent has its `uid`. Changing a review's (or a session's)
opponent from the History screen sets both halves together.

A session's own `cardIds` list is local and is not exported; an import
rebuilds it from the session's reviews.

The review log is the source of truth for progress that is derived, so it
must not double count:

- Every review log has a `uid`; an import skips a log whose `uid` is already
  here.
- A log from a file written before identifiers is the same review when the
  same card has a review at the same moment with the same grade.

The History screen and the retention report name and filter reviews by the
group as it is now (by `groupUid`), falling back to the recorded name for logs
without one.

## Archive (YAML export / backup)

Every record in an archive carries its `uid`, and every reference carries the
referenced record's `uid` where one exists:

- `cards[].uid`, `groups[].uid`, `learningGroups[].uid`, `opponents[].uid`,
  `concepts[].uid`, `sessions[].uid`
- `reviewHistory[].uid`, plus `cardUid`, `groupUid`, `sessionUid` and
  `opponentUid`
- `state.lastReset` and `learningStates[].lastReset`
- `concepts[].global` and `concepts[].availableIn` (learning group names);
  a file from before availability says neither, and an import then leaves
  the concept's availability as it is

Names stay in the file too, for people reading it and for references inside
the file (a card's `groups:` list names groups listed under `groups:`). Every
group a written card is in is listed under `groups:`, so its `uid` travels.

On import, a record is matched **by `uid` first, then by name** (question for
cards, start time for sessions):

- Found by `uid`: it is the same record. It takes the file's name if it was
  renamed (unless that name is taken by another record) and keeps its own
  `uid`.
- Found by name only: it is the same record and keeps its own `uid`.
- Not found: it is created with the file's `uid`.

Files from before identifiers have no `uid`s and import exactly as before.

## Migrations

- Every schema change has a migration; there is deliberately no destructive
  fallback. Room validates the migrated schema against the entities on open,
  and `ConceptSchedulingDbTest` opens a real database built from the previous
  exported schema to prove it.
- A version that has not been released may be amended; a released one never.
- Columns added with `ALTER TABLE ... DEFAULT x` need a matching
  `@ColumnInfo(defaultValue = "x")` on the entity, or Room rejects the
  migrated database.
