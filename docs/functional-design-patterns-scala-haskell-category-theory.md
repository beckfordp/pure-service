# Functional Design Patterns   — Scala, Explained in Haskell & Category Theory

The design patterns this codebase is built on (tagless final, Kleisli composition, `SemigroupK`,
`Writer`, ...), explained through the differing terms Scala (cats/cats-effect), Haskell, and
Category Theory each use for the same underlying structure — a cross-reference for a reader fluent
in more than one of the three, so the differing vocabulary doesn't get in the way of what's actually
going on in the code.

## Contents

- [Programming-language machinery (no direct CT counterpart)](#programming-language-machinery-no-direct-ct-counterpart) — start here: typeclass vs. datatype vs. kind
- [Core algebraic hierarchy](#core-algebraic-hierarchy) — Functor → Apply → Applicative → Monad
- [Structure / composition mechanisms](#structure--composition-mechanisms) — natural transformations, Kleisli, Writer, Free, tagless final
- [Kleisli composition, in depth](#kleisli-composition-in-depth)
- [Kleisli, ReaderT, and Reader — literally the same type](#kleisli-readert-and-reader--literally-the-same-type)
- [How Kleisli's own instances are derived from `F`'s](#how-kleislis-own-instances-are-derived-from-fs)
- [`SemigroupK` — and how it differs from `Semigroup`](#semigroupk--and-how-it-differs-from-semigroup)
- [Cats' `Writer` type](#cats-writer-type)
- [cats-effect specific](#cats-effect-specific)
- [Notes on the Sync / Concurrent / Async hierarchy](#notes-on-the-sync--concurrent--async-hierarchy-cats-effect-specific-no-haskellct-row)
- [System design patterns: tagless final vs. the Cake pattern](#system-design-patterns-tagless-final-vs-the-cake-pattern)
- [http4s' own core typeclasses](#http4s-own-core-typeclasses)

## Programming-language machinery (no direct CT counterpart)

Start here — the deep-dive sections below all lean on this vocabulary.

| Concept | Scala | Haskell | Category Theory |
|---|---|---|---|
| Ad-hoc polymorphism mechanism | "typeclass" (trait + `given`/`implicit`) | "type class" (native `class`/`instance`) | *(none — a PL encoding, not a CT object)* |
| A concrete instance of a typeclass | "instance" / "given instance" | "instance" | *(none — informally, "proof `F` satisfies the axioms")* |
| A concrete carrier type | "datatype" / "data type" | "data type" (`data`/`newtype`) | **Object** (in the category of types) |
| Function between datatypes | "function" / method | "function" | **Morphism** / arrow |
| Type that takes a type parameter | "higher-kinded type," `F[_]`, kind `* -> *` | "type constructor," kind `* -> *` | *(none by that name — it's just the functor's object-mapping)* |

**Typeclass vs. datatype, the actual test**: does the type represent *data* your program is about
(constructed, passed around, returned — a datatype), or a *capability* something else can be shown
to have (summoned implicitly as evidence, never held as business data — a typeclass)? A datatype
**has an instance of** a typeclass — e.g. "`IO` has instances of `Monad`, `Sync`, `Concurrent`,
`Async`"; "`Reservation` has an instance of `Codec`." This is a usage distinction, not something
the language enforces structurally — a typeclass instance is, under the hood, just an ordinary
Scala value like any other.

**Kind is orthogonal to typeclass vs. datatype.** Typeclasses can be parameterized over a
higher-kinded parameter (`Monad[F[_]]`) or an ordinary one (`Codec[A]`, `TextMapGetter[A]`) — same
mechanism either way.

## Core algebraic hierarchy

| Concept | Scala (cats) | Haskell | Category Theory |
|---|---|---|---|
| Structure-preserving map | `Functor[F[_]]`, `.map` | `Functor f`, `fmap` / `<$>` | Functor (here: an endofunctor on the category of types) |
| Combine independent effects, no identity | `Apply[F[_]]`, `.ap`, `.mapN`, `*>`/`<*` | `Apply` (semigroupoids pkg), `<.>` | Lax **semigroupal** functor (no unit) |
| + identity/lift | `Applicative[F[_]]`, `.pure` | `Applicative f`, `pure`, `<*>` | Lax **monoidal** functor (functor + unit + coherent tensor) |
| + data-dependent sequencing | `Monad[F[_]]` (`FlatMap` + `Applicative`), `.flatMap` | `Monad m`, `>>=` (bind), `return`/`pure` | Monad — a **monoid in the category of endofunctors** (unit `η: Id⇒T`, multiplication `μ: T∘T⇒T`) |
| The "multiply" operation | `.flatten` | `join` | μ (mu) — `join = μ`; `flatMap f = μ ∘ F(f)` |
| The "lift" operation | `.pure` | `pure` / `return` | η (eta), unit natural transformation |
| Reverse-direction functor | `Contravariant[F[_]]`, `.contramap` | `Contravariant f`, `contramap` | Functor from `Cᵒᵖ` to `D` |
| Combining structure alone (no map) | `Semigroupal[F[_]]`, `.product` | *(no standalone name in base)* | Functor with tensorial strength (precursor to monoidal) |
| "Combine" algebra on a plain type | `Monoid[A]`, `.combine`, `.empty` | `Monoid a`, `<>`/`mappend`, `mempty` | Monoid object (in a monoidal category) |

*A precision note on "semigroupal functor" (the `Apply` row): it's a genuinely useful, commonly-used
label for "lax monoidal functor minus the unit," but it isn't as canonically established a term as
"lax monoidal functor" itself (the `Applicative` row) — treat it as a good working name rather than
a fixed piece of standard terminology.*

## Structure / composition mechanisms

| Concept | Scala (cats) | Haskell | Category Theory |
|---|---|---|---|
| Map between functors | `FunctionK[F, G]` / `F ~> G` | `forall a. f a -> g a` (no std name; RankNTypes) | Natural transformation |
| Effectful function composition | `Kleisli[F, A, B]`, `.andThen` | `a -> m b`, composed via `>=>` (fish) | Kleisli category (for monad `T`: morphisms `A → T(B)`) |
| Accumulate a monoidal log alongside a value | `Writer[L, A]` / `WriterT[F, L, A]`, `.tell`, `.run` | `Writer w a` / `WriterT w m a`, `tell`, `runWriter` | Monad on the product functor `(L × -)`, induced by `L`'s monoid structure |
| Program-as-data over an algebra | `cats.free.Free[S[_], A]` | `Control.Monad.Free`, `Free f a` | The **free monad** — left adjoint to the forgetful functor `Monad → Endofunctor` |
| Effect-polymorphic encoding (no AST) | "Tagless final" | "**Finally tagless**" (Kiselyov et al. — origin of the Scala term) | "Final" encoding — dual to the free/initial encoding |

The initial vs. final distinction: **initial algebra** (the free monad — program-as-data, an AST)
vs. a **final** encoding (interpret directly via the typeclass's own operations, no intermediate
structure). This terminology is shared verbatim across Haskell and Scala — Scala borrowed it
wholesale from the Haskell/ML "finally tagless" literature.

## Kleisli composition, in depth

A **Kleisli arrow** is just a function `A => F[B]` — a function that returns an effect instead of
a plain value. Two Kleisli arrows `A => F[B]` and `B => F[C]` don't compose with ordinary function
composition (the types don't line up: you'd need `F[B] => C`, not `B => F[C]`). Kleisli composition
is the version of `andThen`/`compose` that does line up, by threading the effect through — and it's
literally `flatMap` under the hood: `(f andThen g)(a) = f(a).flatMap(g)`.

`cats.data.Kleisli[F, A, B]` is a newtype wrapper around `A => F[B]` that gives you `.andThen`,
`.compose`, `.map`, `.flatMap`, etc. for free, forming **the Kleisli category** for the monad `F`
(objects: same as the base category; morphisms `A → B` are really `A → F[B]`; identity is
`Kleisli(a => F.pure(a))`).

### The most relevant example — it's already in this codebase

`HttpRoutes[F]` and `HttpApp[F]` (http4s) are not their own bespoke types — they're **literal
Kleisli type aliases**:
```scala
type HttpApp[F[_]]    = Kleisli[F, Request[F], Response[F]]
type HttpRoutes[F[_]] = Kleisli[OptionT[F, *], Request[F], Response[F]]
```
This is *why* the middleware pattern we've been using (`ServerTracing.middleware`,
`ClientTracing.middleware`) works at all — wrapping "a function from a request to an effectful
response" in another function of the identical shape is precisely composing within the Kleisli
category, whether or not you ever write the word `Kleisli`.

### Rebuilding a familiar handler as explicit Kleisli composition

`InventoryRoutes`'s handler body is a for-comprehension:
```scala
HttpRoutes.of[F] { case req @ POST -> Root / "inventory" / "reserve" =>
  for {
    body        <- req.as[ReserveRequest]
    reservation <- store.reserve(body.item, body.quantity)
    resp        <- Created(reservation)
  } yield resp
}
```
The exact same behavior, written as three named Kleisli arrows composed left-to-right instead of
one inline chain:
```scala
import cats.data.Kleisli

val parseRequest: Kleisli[F, Request[F], ReserveRequest] =
  Kleisli(req => req.as[ReserveRequest])

val reserve: Kleisli[F, ReserveRequest, Reservation] =
  Kleisli(body => store.reserve(body.item, body.quantity))

val respond: Kleisli[F, Reservation, Response[F]] =
  Kleisli(reservation => Created(reservation))

val handler: Kleisli[F, Request[F], Response[F]] =
  parseRequest andThen reserve andThen respond
```
`handler.run(req)` and the for-comprehension version produce identical results — a for-comprehension
over `F` *is* Kleisli composition, just written inline instead of as named, independently reusable
stages. Composing named stages this way is useful when you actually want to reuse or reorder a
piece of the pipeline on its own; inline `for` is usually clearer when you don't.

### A real cats API surface, not just an alias

`org.http4s.client.Client[F]` exposes this directly:
```scala
def toKleisli[A](f: Response[F] => F[A]): Kleisli[F, Request[F], A]
```
"Give me a callback for the response, and I'll hand you back a Kleisli arrow from request to your
result" — a client is, at its core, a Kleisli arrow from `Request[F]` to `F[Response[F]]`
(`Client[F].run(req): Resource[F, Response[F]]` is the resource-scoped cousin of the same idea).

### Haskell side-by-side

```haskell
parseRequest :: Request -> IO ReserveRequest
reserve      :: ReserveRequest -> IO Reservation
respond      :: Reservation -> IO Response

handler :: Request -> IO Response
handler = parseRequest >=> reserve >=> respond
```
`>=>` ("the fish operator," `Control.Monad`) is Haskell's spelling of exactly the same composition
as Scala's `Kleisli(...).andThen(...)` — both are the Kleisli category's composition operator for
the monad in question, just one is a standalone infix operator on plain functions and the other
requires wrapping the function in the `Kleisli` newtype first.

## Kleisli, ReaderT, and Reader — literally the same type

Not just related — in cats they're the *same* type, layered as aliases:
```scala
type ReaderT[F[_], A, B] = Kleisli[F, A, B]
type Reader[A, B]        = ReaderT[Id, A, B]   // Id[X] = X — the "no effect" effect
```
`Kleisli` is the actual definition; `ReaderT` is the name Haskell/mtl users expect ("Reader
transformer"), provided purely for familiarity — same type, same `.andThen`, same everything.
`Reader[A, B]` is what you get when the effect `F` is specialized to `Id`, cats' identity
functor/monad (`Id[X] = X`, no wrapping at all) — a `Reader[A, B]` is just `A => B` dressed in
Kleisli's clothing, "reading" an environment `A` to produce a `B` with no effect involved. So the
whole family collapses to one idea: *Kleisli, with the effect type sometimes trivial (`Id`, giving
you `Reader`) and sometimes real (`IO`, `HttpRoutes`'s `OptionT[F, *]`, etc., giving you `ReaderT`)*.

### `ReaderT`/`Kleisli` as deferred, composable execution

A `Kleisli[F, A, B]` value is inert, the same way a `Resource` or an `IO` value is inert (see the
`Resource`/`.use` section above) — it's a *description* of "given an `A`, here's how to produce an
`F[B]`," not a running computation. Building one, and composing several together with `andThen`,
does no work at all — it just assembles a bigger description out of smaller ones. Nothing executes
until you call `.run(a)` (or `.apply(a)`) and supply the actual environment.

`HttpRoutes[F]` in this project is exactly that in practice: `ServerTracing.middleware(tracer)(...)`
wraps one `Kleisli` value in another, `EmberServerBuilder.withHttpApp(...)` wires the result in —
all of this happens once, at startup, and none of it *runs* anything. The whole routes/middleware
graph just sits there as a composed, unexecuted `Kleisli` value until a real `Request[F]` arrives
and the server calls `.run(req)` on it, per request. Same idea for `Reader`/`ReaderT` generally:
build the computation abstractly in terms of "given the environment, here's the result," compose
freely, and only supply the real environment once, at the very end, when you're ready to run it.

## How Kleisli's own instances are derived from `F`'s

`Kleisli[F, A, B]` doesn't invent its `Functor`/`Applicative`/`Monad`/`SemigroupK` instances from
scratch — each one is mechanically built from the *same* instance on `F` itself (fixing `A`,
varying over `B`):

- **`Functor[Kleisli[F, A, *]]`**, given `Functor[F]`: `map` just post-composes —
  `Kleisli(a => F.map(run(a))(g))`.
- **`Monad[Kleisli[F, A, *]]`**, given `Monad[F]`: this *is* Kleisli composition — `andThen` is
  defined via `F`'s own `flatMap`, exactly as shown above (`f andThen g = a => f(a).flatMap(g)`).
  No `Monad[F]`, no `.andThen` — the capability is borrowed wholesale, not reimplemented.
- **`SemigroupK[Kleisli[F, A, *]]`**, given `SemigroupK[F]`: combine two Kleisli arrows by running
  both and combining their `F[B]` results via `F`'s own `combineK`.

That last one is where your Semigroup instinct was pointing, just needing the precise version —
recall from earlier: a *datatype* **has an instance of** a typeclass, it isn't "a" typeclass. So:
"`Kleisli[F, A, B]` **has a `SemigroupK` instance** whenever `F` does" — not "Kleisli is a
Semigroup." And this isn't abstract — it's exactly what powers a real http4s idiom (not used in
this codebase, but standard elsewhere):
```scala
val combined: HttpRoutes[F] = routes1 <+> routes2   // try routes1; if it 404s, fall through to routes2
```
`HttpRoutes[F] = Kleisli[OptionT[F, *], Request[F], Response[F]]` inherits its `SemigroupK`
straight from `SemigroupK[OptionT[F, *]]`, whose `combineK` means "try the first `OptionT`; if it's
`None`, try the second." Kleisli didn't define route-fallback logic itself — it just forwarded
`OptionT`'s existing `SemigroupK` through its own composition.

### The Semigroup → Monoid direction (and the Apply → Applicative parallel)

One correction: it's `Monoid` that's built **on top of** `Semigroup`, not the reverse —
`Monoid[A] extends Semigroup[A]`, adding an identity element (`empty`) to `Semigroup`'s single
operation (`combine`). This is the *exact same shape* of relationship as `Applicative` on top of
`Apply` from earlier in this doc — `Apply` gives you "combine two independent things"
(`Semigroup`'s job, generalized to `F[_]`), `Applicative` adds "conjure one from nothing"
(`pure`/`empty`). Same pattern, two rungs of the ladder, once for plain types and once for effects:

| Plain type (`cats.kernel`) | Effectful (`cats`) |
|---|---|
| `Semigroup[A]` — `combine` | `Apply[F[_]]` — `ap`/`mapN` |
| `Monoid[A]` — `Semigroup` + `empty` | `Applicative[F[_]]` — `Apply` + `pure` |
| `SemigroupK[F[_]]` — `combineK` | *(the "K" version — see next section)* |
| `MonoidK[F[_]]` — `SemigroupK` + `empty[A]` | |

Where an honest monoid *does* show up directly with Kleisli: fix `A = B` (an **endomorphism**,
`Kleisli[F, A, A]`, "environment and result are the same type"). Composition (`andThen`) is
associative, and `Kleisli(a => F.pure(a))` is a genuine identity element for that composition —
together, effectful endomorphisms under Kleisli composition form a textbook monoid, generalizing
the classic teaching example that plain endofunctions `A => A` form a monoid under ordinary
function composition (identity = `identity`, combine = `andThen`).

## `SemigroupK` — and how it differs from `Semigroup`

The "K" suffix is cats' consistent naming convention for "the higher-kinded version of this
typeclass" — same idea for `MonoidK[F[_]]`, and it shows up elsewhere in cats too. So the guess is
right: `SemigroupK[F[_]]` is `Semigroup` lifted to operate on `F[_]` itself, for *any* `A`, rather
than on one fixed concrete type.

```scala
trait Semigroup[A] {
  def combine(x: A, y: A): A
}

trait SemigroupK[F[_]] {
  def combineK[A](x: F[A], y: F[A]): F[A]
}
```

The difference is more than just "`A` vs `F[_]`," though — it's *what the combining logic is
allowed to look at*:
- `Semigroup[A].combine` is specific to one concrete `A`, and its combining rule is whatever makes
  sense for that particular type — `Semigroup[Int]` via addition, `Semigroup[String]` via
  concatenation. Completely different logic per `A`, chosen ad hoc.
- `SemigroupK[F[_]].combineK` is defined **once**, generically over every possible `A` — it only
  ever touches `F`'s own structure, never the values inside. `SemigroupK[List]`:
  `combineK(xs, ys) = xs ++ ys` — concatenation, regardless of whether the list holds `Int`,
  `String`, or `Reservation`. `SemigroupK[Option]`: `combineK(x, y) = x.orElse(y)` — first-`Some`
  wins, again with zero interest in what's inside.

### Haskell side-by-side

```haskell
-- semigroupoids package — SemigroupK's direct counterpart
class Alt f where
  (<!>) :: f a -> f a -> f a

-- base — MonoidK's direct counterpart (Alt/SemigroupK + an identity)
class Applicative f => Alternative f where
  empty :: f a
  (<|>) :: f a -> f a -> f a
```
`Alt`/`<!>` is Haskell's `SemigroupK`/`combineK`, from the same `semigroupoids` package already
named for `Apply` earlier in this doc. `Alternative`/`<|>` — a genuinely well-known, heavily-used
Haskell typeclass (parsers, `Maybe`, list-based search, all lean on it) — is Haskell's `MonoidK`,
just bundled together with `Applicative` rather than kept as a separate `Monoid`-shaped piece.

Scala's own landing point for that same combination is `cats.Alternative[F[_]]`
(`Applicative[F]` + `MonoidK[F]`) — same shape, same name, even. If `SemigroupK`/`MonoidK` on their
own feel unfamiliar, `Alternative`/`<|>` is almost certainly the term already sitting in memory from
Haskell — same typeclass family, cats just keeps the `Semigroup`-only and `Monoid`-only cuts
available separately (`SemigroupK`, `MonoidK`) in addition to the bundled `Alternative`.

### Where it applies in this project

Honestly: **not currently used** — every service so far has exactly one route match arm
(`HttpRoutes.of[F] { case POST -> Root / "orders" => ... }`), so there's never been two independent
`HttpRoutes[F]` values needing combining. But it's the natural next tool the moment `order-service`
gets a **lookup-by-id endpoint** — not written yet, but a natural near-term addition once Postgres
persistence lands (`OrderStore` currently only supports `create`, no way to fetch an order back).
When that happens, keeping the lookup route as its own, separately-defined, separately-testable
`HttpRoutes[F]` value — rather than folding another `case` arm into `OrderRoutes` itself — and
combining it only at `Main`'s wiring point is exactly where `<+>` earns its keep. Illustrative, not
yet in the codebase:

```scala
// New: a second, independent routes value — not merged into OrderRoutes itself.
object OrderLookupRoutes {
  def routes[F[_]: Concurrent](store: OrderStore[F]): HttpRoutes[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._
    HttpRoutes.of[F] { case GET -> Root / "orders" / orderId =>
      store.find(orderId).flatMap {          // OrderStore would need a `find`, too
        case Some(order) => Ok(order)
        case None        => NotFound()
      }
    }
  }
}

// Main.scala
val routes = ServerTracing.middleware(tracer)(
  OrderRoutes.routes[IO](store, inventory, logger) <+> OrderLookupRoutes.routes[IO](store)
)
```
`<+>` is `SemigroupK`'s infix `combineK` — "run `OrderRoutes`'s routes first; if nothing matches
(an `OptionT.none`), fall through and try `OrderLookupRoutes`'s." Exactly the same
`SemigroupK[OptionT[F, *]]`-inherited-through-`Kleisli` mechanism already covered above.
`OrderRoutes` and `OrderLookupRoutes` never need to know about each other — same "compose
independently-built pieces from outside" spirit as the tracing middleware itself, and it means
`OrderLookupRoutes` gets its own focused test suite rather than growing `OrderRoutesSuite`.

## Cats' `Writer` type

`Writer[L, A]` is the mirror image of `Reader`/`Kleisli`: `Reader` reads an environment *in*,
`Writer` accumulates a log *out*, alongside the actual result — as pure data, with no effect system
involved at all.

```scala
type Writer[L, A]           = WriterT[Id, L, A]
final case class WriterT[F[_], L, A](run: F[(L, A)])
```
Same layering pattern as `Reader`/`ReaderT`/`Kleisli`: `WriterT` is the general effectful version
(wrapping `F[(L, A)]`), `Writer` is the `Id`-specialized, effect-free case (just `(L, A)`).

### Why the log type needs a `Monoid`, not just a `Semigroup`

`WriterT`'s `Monad` instance requires `Monoid[L]`, not merely `Semigroup[L]` — and this is a direct,
concrete instance of the Semigroup → Monoid pattern from the section above:
- `flatMap` runs the first `Writer`, then the second, and **combines** their two logs —
  `Semigroup[L].combine` is all that operation needs.
- `pure`/`Writer.value(a)` has to produce a `Writer` with **no** log entries yet — it needs an
  identity element to start from, which is exactly `Monoid[L].empty`. `Semigroup` alone has no such
  element.

So `Writer` is a clean worked example of why `Applicative`/`Monad` always need the *identity* half
of whatever structure they're built on (`pure` needs something to return "for free"), while `Apply`
alone can get by on combination without it.

### A worked example

```scala
import cats.data.Writer
import cats.syntax.all._

type Logged[A] = Writer[List[String], A]

def reserveStock(item: String, quantity: Int): Logged[Int] =
  for {
    _  <- Writer.tell(List(s"validating $quantity x $item"))
    id <- Writer.value[List[String], Int](42)
    _  <- Writer.tell(List(s"reserved as #$id"))
  } yield id

val (log, reservationId) = reserveStock("widget", 2).run
// log           == List("validating 2 x widget", "reserved as #42")
// reservationId == 42
```
`.tell(entry)` appends to the log and produces `Unit`; `List`'s `Monoid` (`combine = ++`,
`empty = Nil`) is what makes the accumulation work. Nothing here touches `IO`, a `Ref`, or any
effect — `.run` is a pure function, and the log is fully inspectable as ordinary data without
running anything effectful. That's the whole appeal: an audit trail that's part of the return value
itself, not a side effect.

### Haskell side-by-side — and one real gotcha

```haskell
import Control.Monad.Writer

reserveStock :: String -> Int -> Writer [String] Int
reserveStock item quantity = do
  tell [item ++ " x" ++ show quantity ++ " validating"]
  let rid = 42
  tell ["reserved as #" ++ show rid]
  return rid

main = print (runWriter (reserveStock "widget" 2))
-- (42,["widget x2 validating","reserved as #42"])
```
Note the tuple order: Haskell's `runWriter` returns `(a, w)` — value first, log second. Cats'
`.run` returns `(L, A)` — log first, value second. Same structure, reversed tuple order between
the two libraries — easy to trip over if porting intuition directly.

### Not the same thing as `purerest.logging.Logging`

Worth being explicit about this, since "logging" is the shared word but the mechanism is entirely
different: `purerest.logging.Logging.traceCorrelated` performs a **real side effect** — an
`F[Unit]` that actually writes to SLF4J/Logback, sequenced into the surrounding computation via
ordinary `flatMap`. `Writer` performs **no side effect at all** — the "log" is pure, in-memory data
riding along inside the return value, inspected by calling `.run`. Same word, unrelated mechanism;
this codebase's tracing/logging is built entirely on the `F[Unit]`-side-effect style, not `Writer`.

## cats-effect specific

| Concept | Scala (cats-effect) | Haskell | Category Theory |
|---|---|---|---|
| Effect description type | `IO[A]` | `IO a` | *(no canonical single term; treated as an opaque effect monad)* |
| Scoped acquire/release | `Resource[F, A]`, `.use` | `bracket` (`Control.Exception`), `ResourceT` (`resourcet` pkg) | *(engineering construct, not a standard CT name)* |
| Fiber-local, fiber-safe state | `IOLocal`, `cats.mtl.Local[F, Ctx]` | *(no direct standard equivalent — closest is `Reader`-style env threading)* | *(none)* |

## Notes on the Sync / Concurrent / Async hierarchy (cats-effect specific, no Haskell/CT row)

Not part of the classic Functor/Applicative/Monad ladder above, but frequently used together with
it in this codebase:

- **`Sync[F]`**: suspend a synchronous, possibly side-effecting computation (`.delay`, `.blocking`).
- **`Concurrent[F]`**: fork fibers (`.start`), `race`, cancellation, `Ref`/`Deferred`. Siblings with
  `Sync` — neither extends the other.
- **`Temporal[F]`**: `Concurrent[F] + Clock[F]` — adds `.sleep`, timeouts.
- **`Async[F]`**: `Sync[F] + Temporal[F]` — adds the ability to lift an arbitrary callback-based
  asynchronous computation into `F` (`.async_`). The most powerful of the four; strictly extends
  `Concurrent`.

Principle applied throughout this codebase: bind a function to the **narrowest** typeclass that
covers what it actually does (e.g. `InventoryStore` needs only `Sync`; `ServerTracing`/
`ClientTracing` need only `Concurrent`; `HttpClient` needs `Async` because Ember's real async
socket I/O requires it) — never reach for a broader constraint "just in case."

## System design patterns: tagless final vs. the Cake pattern

Everything above this section has been about the *mathematical* vocabulary — what `Functor`,
`Kleisli`, `SemigroupK` etc. mean. This section is different in kind: it's about the *architectural*
pattern this codebase uses to structure itself, named and compared against the other well-known
named pattern for the same general problem in Scala.

### Tagless final, as it's actually used in this codebase

Every capability this codebase depends on is expressed as a **trait parameterised on an effect
type `F[_]`**, with a companion object holding the constructor(s). For example:

```scala
// InventoryStore.scala
trait InventoryStore[F[_]] {
  def reserve(item: String, quantity: Int): F[Reservation]
}
object InventoryStore {
  def inMemory[F[_]: Sync]: F[InventoryStore[F]] = ...
}

// InventoryClient.scala
trait InventoryClient[F[_]] {
  def reserve(item: String, quantity: Int): F[ReservationView]
}
object InventoryClient {
  def apply[F[_]: Concurrent](client: Client[F], baseUri: Uri): InventoryClient[F] = ...
}
```

The trait is the **algebra**: a signature of operations, with no commitment to how `F` executes
them. The companion's `inMemory`/`apply` is an **interpreter**: one concrete choice of `F` and one
concrete implementation, built with just enough constraint (`Sync`, `Concurrent`, ...) to do the
job — the same "narrowest typeclass that covers what it actually does" discipline from the
Sync/Concurrent/Async section above. Wiring is nothing more exotic than passing the interpreter as
an ordinary function/constructor argument: `OrderRoutes.routes(orderStore, inventoryClient, logger)`.
This whole trait+companion shape (algebra as trait, interpreter as companion-object constructor,
composition by parameter-passing) *is* what "tagless final" means as a concrete Scala idiom — the
CT-flavoured framing (an algebra `F[_]` interpreted directly into some concrete `Monad`, the "final"
encoding, as contrasted with Free's "initial" encoding) is covered in
[Structure / composition mechanisms](#structure--composition-mechanisms) above; this section is
about what that buys you architecturally, and what it costs compared to the alternative.

**Worth being precise about what is, and isn't, "tagless final" in this codebase.** `InventoryStore`,
`InventoryClient`, `OrderStore`, and purerest's own `ServerTracing`/`ClientTracing`/`Logging` are
tagless-final algebras *we* wrote. **http4s itself is not "an instance of" this pattern** — it's a
library that happens to expose an `F[_]`-polymorphic API (`HttpRoutes[F]` is a `Kleisli`, as covered
earlier; `Client[F]`, `EntityDecoder[F, A]`, etc.), which is precisely *why* it composes cleanly
inside tagless-final code without forcing a concrete effect type on us. `ServerTracing.middleware`
is a good example of the seam: it takes an `HttpRoutes[F]` (http4s' machinery) and a `Tracer[F]`
(our tagless-final capability) and returns a new `HttpRoutes[F]` — our pattern *wrapping* library
code, not the library being reimplemented in our pattern.

### Tagless final vs. Free — the same initial/final distinction, applied to "why not Free here"

This project could, in principle, have modelled a capability like `InventoryClient` as a **Free
monad** instead — build an algebra of instructions as a data type, then interpret it later:

```scala
sealed trait InventoryClientOp[A]
final case class Reserve(item: String, quantity: Int) extends InventoryClientOp[ReservationView]

type InventoryClientProgram[A] = Free[InventoryClientOp, A]

def reserve(item: String, quantity: Int): InventoryClientProgram[ReservationView] =
  Free.liftF(Reserve(item, quantity))

// later, separately:
val interpreter: InventoryClientOp ~> IO = new (InventoryClientOp ~> IO) {
  def apply[A](op: InventoryClientOp[A]): IO[A] = op match {
    case Reserve(item, quantity) => client.expect[ReservationView](...)
  }
}
program.foldMap(interpreter)
```

That's the **initial encoding**: `Reserve` is inert *data* describing a call, reified as an AST node,
which some separate natural transformation (`InventoryClientOp ~> IO`) later folds into a real
effect via `foldMap`. What this codebase does instead — `InventoryClient[F]` as a trait, `apply`
as a direct interpreter into `F` — is the **final encoding**: no AST, no `foldMap` step; calling
`inventoryClient.reserve(...)` *is already* the effect, immediately, in whatever `F` the caller
picked. Same algebra, same intent (describe a capability abstractly, defer choosing its concrete
implementation); the difference is only *when* interpretation happens and whether there's a
reified data structure standing in for it in between. This is exactly the initial-vs-final framing
from the [Structure / composition mechanisms](#structure--composition-mechanisms) table, now made
concrete with this project's own `InventoryClient`.

Practically: Free buys you the ability to *inspect or rewrite the program as data* before running it
(optimise a sequence of calls, log/replay it, test against the AST without running anything) at the
cost of an extra indirection layer (the ADT, the natural transformation, `foldMap`) and typically
worse performance (each step boxes into a `Free` node). Nothing in this codebase currently needs
program-as-data — every capability is interpreted once, directly, against a single real or in-memory
`F` — so the tagless-final/final-encoding route is the simpler tool for the job actually at hand.

### The Cake pattern — the other named Scala answer to "how do I wire dependencies"

The **Cake pattern** (Jonas Bonér's original formulation, popular in Scala before tagless-final
became the community default) solves a related but distinct problem: wiring a *graph* of concrete
components together using the type system, with no runtime DI framework. Its mechanism is
**self-types** plus **trait mixin composition**, not constructor parameters:

```scala
// Illustrative only — NOT how this codebase is structured.
trait InventoryStoreComponent {
  def inventoryStore: InventoryStore
}

trait InventoryRoutesComponent { self: InventoryStoreComponent =>
  // `self:` lets this trait call `inventoryStore` without declaring it as a
  // parameter anywhere — it's a promise that whatever concrete object mixes
  // this trait in will *also* mix in something providing `inventoryStore`.
  val inventoryRoutes = new InventoryRoutes(inventoryStore)
}

// The "cake" is baked at exactly one place: an object that mixes in every
// component trait the graph needs, providing the concrete implementations.
object Application extends InventoryRoutesComponent with InventoryStoreComponent {
  val inventoryStore = new InMemoryInventoryStore()
}
```

Compare to how this project wires the equivalent dependency — just passing a value:

```scala
val store  = InventoryStore.inMemory[IO]
val routes = InventoryRoutes.routes[IO](store, logger)
```

Cake earns its keep on *large, deeply-layered graphs of concrete components* — dozens of
interdependent pieces where writing out every constructor parameter by hand becomes unwieldy, and
where you want the compiler to catch "you forgot to wire X" at the mixin site. Its costs are real,
though, and are why the wider Scala community has largely moved away from it: self-type resolution
errors are notoriously hard to read, `val` initialization order across mixed-in traits is a classic
footgun (a `val` in one trait can observe an as-yet-uninitialized `val` from another), and — the
point most relevant here — **Cake has nothing to say about effect polymorphism**. It wires concrete
*objects* together; it doesn't abstract over "which `F[_]` is this running in," the way `trait
InventoryStore[F[_]]` does natively. You'd need to bolt something else on top of Cake to get that.
Tagless final gets you both effect polymorphism *and* swappable implementations from the same single
mechanism (a type parameter and a trait), at the cost of writing dependencies out explicitly as
parameters — which is exactly why, for a codebase this size, this project reaches for tagless final
and has no use for Cake at all.

*Haskell side note:* Haskell has no self-type/mixin-composition feature, so there's no direct Cake
analogue to translate. The idiomatic Haskell answer to "wire a big graph of app dependencies" is
usually what the production-Haskell community calls the **`ReaderT` pattern** — bundle everything the
app needs into one `Env` record and thread it via `ReaderT Env IO` (optionally with `mtl`-style
`Has*` typeclasses for individual fields). That's much closer in spirit to this project's
constructor-parameter tagless-final style — and to the `Kleisli`/`ReaderT` material already covered
in [Kleisli, ReaderT, and Reader — literally the same type](#kleisli-readert-and-reader--literally-the-same-type)
— than it is to Cake's self-type mixin machinery.

### Scaling tagless final without Cake: bundling dependencies into `Resource`-built modules

Large fanout (a service calling many other services, each needing its own client) is exactly the
scenario Cake is pitched at. In practice, production http4s/cats-effect codebases mostly solve it
without Cake at all — a well-known real example is Gabriel Volpe's
[*Practical FP in Scala* shopping-cart app](https://github.com/gvolpe/pfps-shopping-cart), which
groups related dependencies into small case classes built via `Resource[F, _]`, composed the same
way, layer by layer. Still plain tagless final, still ordinary constructor parameters — just
*grouped*, so the parameter count per layer stays small instead of growing with the whole graph.

Illustrative for this project: if `order-service` grew to depend on inventory, payment, shipping,
and notification clients, the fanout would otherwise show up as
`OrderRoutes.routes(store, inventoryClient, paymentClient, shippingClient, notificationClient, logger)`.
Bundling the sibling clients into one case class keeps every step a plain value:

```scala
// Each client is a normal tagless-final algebra — same shape as this
// project's real InventoryClient, nothing new.
trait InventoryClient[F[_]]    { def reserve(item: String, qty: Int): F[ReservationView] }
trait PaymentClient[F[_]]      { def charge(orderId: String, amountCents: Long): F[PaymentReceipt] }
trait ShippingClient[F[_]]     { def schedule(orderId: String, address: Address): F[ShipmentId] }
trait NotificationClient[F[_]] { def orderConfirmed(orderId: String): F[Unit] }

final case class Clients[F[_]](
  inventory: InventoryClient[F],
  payment: PaymentClient[F],
  shipping: ShippingClient[F],
  notification: NotificationClient[F]
)

object Clients {
  final case class Config(
    inventoryBaseUri: Uri,
    paymentBaseUri: Uri,
    shippingBaseUri: Uri,
    notificationBaseUri: Uri
  )

  // One shared http4s Client[F] (one connection pool), fanned out into
  // four algebra instances that each close over their own base Uri.
  def resource[F[_]: Async: Network](config: Config): Resource[F, Clients[F]] =
    HttpClient.resource[F].map { httpClient =>
      Clients(
        inventory    = InventoryClient[F](httpClient, config.inventoryBaseUri),
        payment      = PaymentClient[F](httpClient, config.paymentBaseUri),
        shipping     = ShippingClient[F](httpClient, config.shippingBaseUri),
        notification = NotificationClient[F](httpClient, config.notificationBaseUri)
      )
    }
}
```

`OrderRoutes` now takes one `Clients[F]` parameter instead of four, and `Main` composes exactly one
`Resource[F, Clients[F]]`:

```scala
object OrderRoutes {
  def routes[F[_]: Concurrent](
    store: OrderStore[F],
    clients: Clients[F],
    logger: StructuredLogger[F]
  ): HttpRoutes[F] = HttpRoutes.of[F] {
    case req @ POST -> Root / "orders" =>
      for {
        create      <- req.as[CreateOrderRequest]
        reservation <- clients.inventory.reserve(create.item, create.quantity)
        receipt     <- clients.payment.charge(create.item, create.quantity * 100L)
        _           <- clients.shipping.schedule(reservation.id, create.address)
        _           <- clients.notification.orderConfirmed(reservation.id)
        order       <- store.create(create.item, create.quantity, reservation.id)
      } yield Response[F](Status.Created).withEntity(order)
  }
}

object Main extends IOApp.Simple {
  def run: IO[Unit] =
    (for {
      clients <- Clients.resource[IO](Clients.Config.fromEnv)
      store   <- Resource.eval(OrderStore.inMemory[IO])
      routes  = OrderRoutes.routes[IO](store, clients, Slf4jLogger.getLogger[IO])
      _       <- EmberServerBuilder.default[IO].withHttpApp(routes.orNotFound).build
    } yield ()).useForever
}
```

The trick generalises by nesting: if `Clients` itself grew large, split it into e.g.
`ExternalClients`/`InternalClients` and bundle those two the same way, one level up. Every step
stays a plain value and a plain `Resource` — no self-types, no mixin composition, no compile-time
cake — which is why this is the pattern the wider ecosystem reaches for instead of Cake, even at
real production fanout.

**Illustrative only** — this section's code (`PaymentClient`, `ShippingClient`, `NotificationClient`,
`Clients`) is not part of this codebase; `order-service` currently depends only on `InventoryClient`.

## http4s' own core typeclasses

Not new machinery — http4s' own API surface is built directly out of the vocabulary already covered
above, applied to HTTP specifically.

### `EntityDecoder[F[_], A]` / `EntityEncoder[F[_], A]`

How a request/response body gets turned into (or out of) a Scala value, inside `F`. This codebase
never hand-writes instances: `org.http4s.circe.CirceEntityCodec._` (imported in `OrderRoutes`,
`InventoryClient`, `OrderServiceTraceContinuitySuite`) derives both automatically from a circe
`Decoder[A]`/`Encoder[A]` — which is why `req.as[CreateOrderRequest]` and `.withEntity(order)` just
work once `Codec[Reservation]` exists via `deriveCodec`. The body itself,
`EntityBody[F] = Stream[F, Byte]`, is an fs2 stream — so decoding/encoding is inherently effectful
and streaming, not "parse a string," consistent with this codebase staying `F`-polymorphic rather
than assuming a concrete runtime anywhere.

### `HttpApp`/`HttpRoutes` are literal `Kleisli` aliases — not new types

This is the payoff of [Kleisli composition, in depth](#kleisli-composition-in-depth) above, not a
new idea. http4s' two central type aliases are defined directly off `Kleisli`:

```scala
type HttpApp[F[_]]    = Kleisli[F, Request[F], Response[F]]                  // total: always answers
type HttpRoutes[F[_]] = Kleisli[OptionT[F, *], Request[F], Response[F]]      // partial: may say "not mine"
```

`HttpApp` is total — every request gets *some* response. `HttpRoutes` is partial via `OptionT`:
`None` means "not my route," letting the framework fall through to a 404 (or the next route set).
`ServerTracing.middleware`'s signature, `HttpRoutes[F] => HttpRoutes[F]`, is exactly
`Kleisli[OptionT[F, *], Request[F], Response[F]] => (same)` — a **middleware**, which http4s itself
defines as nothing more than a function between two `Kleisli`s. No new machinery; it's Kleisli
composition, applied.

### Where `SemigroupK` actually shows up in http4s

The [`SemigroupK`](#semigroupk--and-how-it-differs-from-semigroup) section above used a hypothetical
example, since nothing in this project used it yet at the time. http4s itself does, centrally:
`HttpRoutes[F]` has a `SemigroupK` instance (inherited from `OptionT[F, *]`'s), so
`routes1 <+> routes2` means "try `routes1`; if it returns `None` (not my route), fall through to
`routes2`." That's the real mechanism behind combining several route definitions into one `HttpApp`
— `combineK`/`<+>`, same typeclass, same "first success wins" semantics described abstractly
earlier, now with a canonical concrete user.

### `Client[F[_]]` — http4s' own algebra, sitting *under* ours

`Client[F[_]]` is itself shaped like a tagless-final algebra http4s ships you, not something you
write — roughly:

```scala
trait Client[F[_]] {
  def run(req: Request[F]): Resource[F, Response[F]]  // Resource: a connection needs releasing
}
```

`InventoryClient[F]`, this project's own algebra, is built *on top of* `Client[F]`
(`client.expect[ReservationView](...)`) rather than replacing it — a concrete instance of the
"http4s fits inside our tagless-final code, it isn't itself an instance of our pattern" distinction
from the [design patterns section](#system-design-patterns-tagless-final-vs-the-cake-pattern) above,
though it's worth noting `Client[F]`'s own shape happens to look the same way.

### Why `EmberServerBuilder`/`EmberClientBuilder` need `Async[F]`

Real non-blocking socket I/O needs to register a callback with the OS/NIO layer and resume the
fiber later — exactly the capability the
[Sync/Concurrent/Async section](#notes-on-the-sync--concurrent--async-hierarchy-cats-effect-specific-no-haskellct-row)
names as `Async`'s one differentiator (`.async_`, lifting a callback-based computation into `F`).
`Sync` or `Concurrent` alone can't do it, which is why `HttpClient.resource[F[_]: Async: Network]`
sits above the "narrowest typeclass" line the rest of this codebase otherwise stays under.
