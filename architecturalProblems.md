# Architectural Problems: Duplicate Library Versions on the Classpath

Analysis triggered by a fatal startup error of the Maven-built fat jar
(`target/magellan2-2.1.2.jar`), 2026-08-21:

```
java.lang.IncompatibleClassChangeError: class com.jgoodies.looks.windows.WindowsMenuItemRenderer
    cannot inherit from final class com.jgoodies.looks.common.MenuItemRenderer
    at magellan.client.utils.RendererLoader.loadRenderers(RendererLoader.java:122)
    at magellan.client.Client.initComponents(Client.java:861)
```

The crash was fixed (see "Already addressed" below). This document records the
underlying architectural problems, which are **pre-existing** — the Maven fat
jar only made them visible.

## Problem 1: Two versions of the same library share one namespace

`lib/skins/skinsCollected.jar` contains a complete JGoodies Looks 2.x-era
distribution in addition to many other Look & Feels:

- All 217 `com.jgoodies.looks.*` classes of `lib/skins/looks-1.3b1.jar` are also
  present in skinsCollected (verified: zero classes are unique to 1.3b1), but
  as the newer 2.x versions (e.g. `MenuItemRenderer` is `final` in 1.3b1,
  non-final in 2.x; `WindowsMenuItemRenderer` only exists in 2.x).
- All 48 non-class jgoodies resources (icons etc.) are present in both jars.

Version resolution relies entirely on classpath order: the classic release
manifest (`etc/manifest.mf.template`) lists `looks-1.3b1.jar` before
`skinsCollected.jar`, so 1.3b1 wins by shadowing. "First jar on the classpath
wins" is an implicit, fragile version-resolution strategy that breaks silently
whenever a tool changes the effective ordering — exactly what happened when the
Maven build merged all jars into one fat jar.

## Problem 2: skinsCollected.jar is an undocumented library grab-bag

The jar (dated 2006) bundles roughly eight libraries and duplicates content of
three other shipped jars:

| Overlapping classes | Also contained in |
|---|---|
| 32 `com/incors/plaf/*` | `kunststoff-2_0_2.jar` |
| 6 `org/jvnet/lafplugin/*` | `liquidlnf-2.9.1.jar` |
| 133 `com/stefankrause/xplookandfeel/*` | `xplookandfeel.jar` (byte-identical) |
| 217 `com/jgoodies/looks/*` | `looks-1.3b1.jar` (different version!) |

Plus unique content: TinyLaF, Metouia, Squareness, Fh (shfarr), Pgs
(pagosoft), winlaf (`net.java.plaf`). The duplication went unnoticed for ~20
years because classpath ordering masked it.

## Problem 3: Eager vs. lazy linkage — more latent landmines

`WindowsMenuItemRenderer` was special only because its incompatibility was
*structural* (a class extending a `final` class): the JVM rejects that eagerly
at load/link time, so it exploded during the renderer scan.

About 43 other 2.x-only jgoodies classes from skinsCollected remain in both the
classic install and the fat jar. Their incompatibilities fail **lazily** — only
when actually executed — with `NoSuchMethodError`, `NoSuchFieldError` or
`NoClassDefFoundError`. Concrete example (verified via constant pool): the 2.x
`WindowsMenuItemUI` references `WindowsMenuItemRenderer`; instantiating it now
throws `NoClassDefFoundError` (the classic install would throw
`IncompatibleClassChangeError`).

Mitigating circumstance: nothing in Magellan touches these classes. The code
has no jgoodies API usage (only commented-out imports), and every jgoodies
Look & Feel listed in `etc/plaf.ini` (`PlasticLookAndFeel`,
`Plastic3DLookAndFeel`, `PlasticXPLookAndFeel`, `ExtWindowsLookAndFeel`)
resolves to self-consistent 1.3b1 classes. The 2.x-only classes are dead code
in this classpath layout.

## Problem 4: RendererLoader's classpath-scanning design

`magellan.client.utils.RendererLoader` scans every `*.jar`/`*.zip` in the
working directory and reflectively loads every entry ending in
`Renderer.class` via a parent-first-delegating class loader. This links
arbitrary foreign classes into the application's namespace, resolved against
the application classpath (not the scanned jar's own dependencies). Before the
fix it only caught `ClassNotFoundException`, so any `LinkageError` from any
jar in the launch directory killed the whole client at startup.

Escape hatch: setting the property `RendererLoader.dontSearchAdditionalRenderers=true`
disables the scan entirely.

## Already addressed (2026-08-21)

- `RendererLoader` now catches `LinkageError`, logs the offending class and
  skips it instead of crashing the client.
- The Maven fat-jar merge (`pom.xml`) uses explicit `zipfileset` entries in the
  exact classpath order of `etc/manifest.mf.template` (Ant processes
  `zipgroupfileset`s after `zipfileset`s regardless of document order, which
  would silently flip precedence), and excludes the unusable
  `com/jgoodies/looks/windows/WindowsMenuItemRenderer*.class` from
  skinsCollected.
- Regression test: `src-test/magellan/client/utils/RendererLoaderTest.java`.
- Verified: the fat jar now resolves byte-identical class versions to the
  classic release for every overlapping package; all 91 `*Renderer.class`
  entries load cleanly.

## Durable fixes (options, in rising effort)

1. **Strip `com/jgoodies/looks/**` from skinsCollected.jar** (repack it,
   keeping the independent L&Fs) → a single Looks version everywhere.
   Requires re-testing the Plastic L&Fs and Pgs (its `JGoodiesThemes` may want
   the 2.x classes).
2. **Or drop looks-1.3b1.jar** and let skinsCollected's complete 2.x copy win
   (upgrade path; Plastic L&F rendering may subtly change, and the provenance
   of that 2.x build is unknown).
3. **Replace classpath scanning with explicit renderer registration** (e.g.
   `ServiceLoader` or a plugin descriptor listing renderer classes) →
   eliminates the entire failure class. Only worth it if third-party map
   renderers actually matter — the project ships none.
4. **Long-term**: real versioned dependencies from Maven Central where
   available (JGoodies Looks is available; the exotic skin jars are not).

Recommendation: keep the crash fix as-is; treat the skinsCollected cleanup as
its own change with Look & Feel testing, not as part of a crash hotfix.
