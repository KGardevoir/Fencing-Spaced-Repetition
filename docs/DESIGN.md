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

Existing rows got a fresh `uid` in the 17 -> 18 migration (17 -> 22 on master's line). Review logs got
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
history with it. A review logged before groups had a `uid` is found by the
name it recorded, the oldest group of that name.

`LearningContexts` (shared `data/model`) is the one implementation of these
rules; scheduling, concept derivation, the retention report and the screens
all go through it.

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

### Weights

Each tag carries a **weight** (`card_concept_cross_ref.weight`): how much of
the concept the card contains, **relative to the card's other concepts**. Any
number of 0 or more (default 1) -- "2 of X, 1 of Y" is weights 2 and 1. Only
the proportions matter: scoring uses each card's **share** of each concept,
its weights divided by their sum (`conceptShares`), so a card's shares add to
1 and a card with one concept is all of it whatever the number. A card whose
weights are all 0 contains none of its concepts.

- **Grading.** A session's grade for a concept is the mean of its cards'
  grades **weighted by each card's share** of the concept, rounded to the
  nearest grade; a card with share 0 says nothing about it. A review outside
  a session is graded as it was (its share only decides whether it counts).
- **Drawing.** A session is drawn by **dot product**. Every concept (and
  every untagged card) has a **need**: 1 minus FSRS's predicted recall of it
  right now in that learning context -- 1 if never learnt -- or 0 when it is
  not due. A card's score is the sum over its concepts of its **share** times
  the concept's need: the need it would meet. To choose, that is multiplied
  by the card's **staleness** -- 0.5 just after it was graded in that
  context, rising towards 1 with the days since (`1 - e^(-days/7)` scaled
  into 0.5..1), 1 if never graded there -- so the cards that cover a concept
  take turns instead of the same one winning every time. The highest-scoring
  card is taken, each of its concepts' need drops by its share (not below 0), and
  the next is chosen the same way, until the session is full or no card
  meets any need; the rest is topped up with additional study. Ties go to
  the lowest id, or at random where the group randomises. Because shares
  add to 1, every card meets at most 1 unit of need: a card half about two
  needed concepts scores the same as one all about one of them, and leaves
  half of each for others (`greedyDotProduct`).

Changing any of a card's weights rederives all of its concepts, since every
share on the card moves. In the archive a card's `conceptWeights:` maps
concept name to weight, listing only weights that are not 1; a concept
missing from it -- and every concept in a file from before weights -- is 1.

### Tagging and availability

Cards are tagged with concepts from the **card editor**; the Concepts screen
defines concepts and where they are available, and shows how many cards use
each, but does not tag cards.

A concept is **available** in learning contexts: the learning groups it is
linked to (`concept_learning_group`) and Global when `concepts.inGlobal`. A
concept with **no availability set** -- neither -- is **available
everywhere**: setting availability restricts a concept. So every concept made
before availability existed, and every one imported from a file from before
it, stays usable without anyone setting anything. The
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
`useConcepts` switch and Global has a setting. Where it is on, a session is drawn by
the dot product of each card's shares with its concepts' need (see Weights); elsewhere,
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

### Why a card was practised

Every review written since version 21 (24 for `selectionTrigger`) also records, for later analysis,
why the card was in front of the fencer and against whom:

| Column | What it records |
|---|---|
| `selectionMode` | How the session was drawn: `CARD` (card by card) or `CONCEPT` |
| `selectionReason` | Why this card: `DUE`; `EXTRA` (topped up a session the due cards did not fill); `LOCKED` (the group's draw was locked, so earlier cards were kept); `EDITOR` (graded in the card editor, not drawn) |
| `selectionConcepts` | Drawn by concept: the `uid`s of every concept whose need the card met (a term of its score above 0), comma-separated; empty for an untagged card or extra study |
| `selectionTrigger` | Drawn by concept: the `uid` of the concept the card was drawn for. Reason `DUE`: the concept that contributed the most to its dot-product score (its share times that concept's need), one of `selectionConcepts`. Reason `EXTRA`: its concept coming due soonest, which put it first in line for the top-up. Reason `LOCKED`: the one from the draw the lock kept. Null for a card with no concepts or not drawn by concept (since version 24) |
| `opponentSkillMultiplier` | The opponent's skill multiplier at the time, before fight difficulty |

With `fightDifficulty` (1-5) and `stabilityMultiplier` (the two
multipliers combined, as applied) already on every review, a past review
can be taken apart into opponent and fight even after the opponent's skill
is edited. All are null on reviews from before they existed. They are
history: written once, exported (`reviewHistory[].selectionMode`,
`selectionReason`, `selectionConcepts`, `selectionTrigger`,
`opponentSkillMultiplier`) and
imported with a new review, never changed by a later import.

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

### An import updates everything but progress

So that another program can edit an export -- rename, rewrite, regroup,
retag, re-describe -- and the result can be imported without resetting
anyone's learning, a record already here takes everything from the file
**except progress**:

| Record already here | Taken from the file | Kept |
|---|---|---|
| Card | question, description, pictures, groups, concepts | scheduling state, last review, next review, `lastReset`, every learning-group state; `uid`, creation time, disabled |
| Concept | name, description, availability | its progress (derived from history anyway) |
| Card group | name, description, learning group, auto-show | -- |
| Learning group | name, description, every setting, `useConcepts` | -- |
| Opponent | name (when free), skill multiplier, notes | -- (past reviews keep the multiplier they were scheduled with) |
| Session | notes and their pictures | times, cards, grades |
| Review | notes, pictures, opponent | grade, time, group, multiplier -- the history progress is derived from |

A record **new** here is created from the file whole, progress included, so
restoring a backup onto a new device restores its learning.

**Absent is not empty.** Where a newer format added a list an older file
never wrote, the list is now always written -- a card's `concepts:` and a
concept's `availableIn:` -- even when empty. Present (even `[]`) sets the
value; absent, as in a file from before it, leaves the value as it is here. A
group's `description:` works the same way. So does a card that is only a
question and an answer, as in a hand-written deck: it says nothing about
groups or pictures, and leaves the card's as they are.

Every archive import goes through `CardArchive.importCards`, so these rules
hold whatever the file carries.

## Migrations

- Every schema change has a migration; there is deliberately no destructive
  fallback. Room validates the migrated schema against the entities on open,
  and `ConceptSchedulingDbTest` opens a real database built from the previous
  exported schema to prove it.
- **A schema version is released the moment any build that uses it is
  installed** -- a debug or CI build counts. After that it never changes:
  Room records the schema's identity hash, and a phone whose database has
  a different hash under the same version crashes on launch. Every change
  goes in a new version with its own migration. (Version 18 was once
  amended in place after debug builds were installed; 18 -> 19 brings
  every one of those variants up to date, and `Migration18To19Test` fails
  if the committed version 18 schema ever drifts from the released one.)
- **Two lines meet at 23.** Identifiers were first added here, at 18,
  together with concepts; builds of 18 to 21 are installed. They were then
  split out to master on their own, which skipped those numbers and went
  17 -> 22 (`MIGRATION_17_22`, `MIGRATION_SKIPPED_VERSIONS`). 23 joins the
  two: from master's 22, `MIGRATION_22_23` adds everything concepts brought
  (tables, availability, weights, the switch, why a card was practised);
  from this line's 21, `MIGRATION_21_23` has nothing to do, as 23 is 21's
  schema under a new number. From 17, Room takes the longest step it can,
  so it goes 17 -> 22 -> 23; a database on 18 to 21 goes on to 21, then 23.
  `Migration22To23Test` checks both lines reach the same tables.
- The exported schemas of the variants that were installed are kept in
  `shared/src/jvmTest/resources/released-v18/`, and `ConceptSchedulingDbTest`
  upgrades a database built from each. Each later released version's schema
  is kept in `shared/src/jvmTest/resources/released/` (19, 20, 21, master's 22 and 23 so far), with an
  upgrade test from it and a guard test that fails if the committed schema
  for that version ever changes. Add one for each version you release.
- Columns added with `ALTER TABLE ... DEFAULT x` need a matching
  `@ColumnInfo(defaultValue = "x")` on the entity, or Room rejects the
  migrated database.
