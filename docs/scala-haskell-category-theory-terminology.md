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
| Program-as-data over an algebra | `cats.free.Free[S[_], A]` | `Control.Monad.Free`, `Free f a` | The **free monad** — left adjoint to the forgetful functor `Monad → Endofunctor` |
| Effect-polymorphic encoding (no AST) | "Tagless final" | "**Finally tagless**" (Kiselyov et al. — origin of the Scala term) | "Final" encoding — dual to the free/initial encoding |

The initial vs. final distinction: **initial algebra** (the free monad — program-as-data, an AST)
vs. a **final** encoding (interpret directly via the typeclass's own operations, no intermediate
structure). This terminology is shared verbatim across Haskell and Scala — Scala borrowed it
wholesale from the Haskell/ML "finally tagless" literature.

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
