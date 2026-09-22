# Scala / Haskell / Category Theory Terminology

A cross-reference for the terms used interchangeably (or near-interchangeably) across Scala
(cats/cats-effect), Haskell, and Category Theory, as used in this codebase.

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

## Programming-language machinery (no direct CT counterpart)

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
