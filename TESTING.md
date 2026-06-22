# Testing

The `.cljc` domain interpreters (`kami.fsm`, `kami.physics`, `kami.netsync`, `kami.level`,
`kami.webgpu.ir`) are the **same source** that runs on three surfaces. They're tested on all
three so cross-platform behaviour can't drift:

| Surface | How it runs the interpreters | Test command |
|---|---|---|
| **web** | CLJS → WebGPU / DOM (`kami.webgpu`, `kami.ui`, …) | in-browser (isekai.network) |
| **native** | CLJ data subset → WASM (kotoba-clj) → `kami-script-runtime` | `cargo test -p kotoba-clj --test keystone_domains` |
| **JVM** | babashka loads the `.cljc` directly | `bb test` |

## Commands

```bash
bb test       # JVM: the .cljc interpreters (8 tests / 42 assertions — examples + properties)
bb verify     # all three surfaces: bb test + kotoba-clj WASM + kami-webgpu-rs native renderer
```

`bb verify` shells into the sibling `../kotoba` and `../kami-engine` checkouts, so it expects
the superproject layout. CI (`.github/workflows/test.yml`) runs `bb test` on every push — the
fast, GPU-free correctness gate. The native renderer's GPU tests (`renders_geometry_headless`,
`caster_casts_a_shadow`) need a real GPU (Metal/Vulkan) and run locally via
`cargo test -p kami-webgpu-rs`, not on headless CI.

## What's asserted

- **fsm** `advance` — transitions fire on matching events; identity otherwise
- **physics** `collides?` (symmetric) + `separate` (overlap → deltas; non-colliding → none)
- **netsync** `snapshot` (synced fields only, idempotent) + `interp` (lerp/snap, t-endpoints)
- **level** `zone-radius` (monotonic, floors at `:min-radius`) + `in-zone?` + `spawn-points`
- **camera** `rig->camera` (distance/azimuth/height → eye/target)
- **ir** `render-ir` / `valid?`
