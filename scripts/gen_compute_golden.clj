;; gen_compute_golden.clj — single-source the cartpole compute-golden (math hash + emitted WGSL)
;; from CLJC data. Phase 3.1 (ADR-2607010930, re-scoped to kami-webgpu).
;;
;; The compute-golden pins two artifacts:
;;   1. fixtures/cartpole-compute-step.wgsl  — the WGSL string `kami.physics-compute/cartpole-step-emit`
;;      produces (the @compute kernel the GPU would run).
;;   2. fixtures/cartpole-compute-golden.json — a fixed input → SHA-256 of the output buffer state,
;;      where the output state is computed by the pure-CLJC mirror in `kami.cartpole-math`.
;;
;; Regenerate both, then assert `git diff --exit-code` is clean — the same "single source, no drift"
;; guarantee `gen_wgsl.clj` gives the lit/shadow shaders. The committed fixtures are what `bb test`
;; (compute-golden test) asserts against, so a divergence in EITHER the emitter OR the math fails
;; its test instead of drifting silently.
;;
;; This script is run by the `bb gen-compute-golden` task with a classpath that puts the kami-engine
;; SDK FIRST so its `kami.wgsl` (the high-level `emit`/`emit-compute-stage` API) resolves — kami-webgpu
;; ships a different, low-level `kami.wgsl`, so the SDK must win on this process's classpath:
;;
;;   bb --classpath ../kami-engine/kami-engine-sdk-clj/src:src -f scripts/gen_compute_golden.clj
;;
;; (`--classpath` overrides bb.edn `:paths`, so kami-webgpu's `src` is still available for
;; `kami.cartpole-math`, but the SDK's `kami.wgsl` is found first for `kami.physics-compute`.)
(ns gen-compute-golden
  (:require [kami.cartpole-math :as cm]
            [kami.physics-compute :as pc]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private sdk-root
  "../kami-engine/kami-engine-sdk-clj/src/kami/physics_compute.cljc")

(def ^:private wgsl-fixture  "fixtures/cartpole-compute-step.wgsl")
(def ^:private golden-fixture "fixtures/cartpole-compute-golden.json")

(defn- exit! [msg]
  (binding [*out* *err*] (println msg))
  (System/exit 1))

(when-not (.exists (io/file sdk-root))
  (exit!
    (str "gen-compute-golden: kami-engine SDK not found at " sdk-root "\n"
         "  This script needs the sibling `kami-engine` repo for `kami.physics-compute/cartpole-step-emit`.\n"
         "  Run `west update --fetch smart kami-engine` (or clone kotoba-lang/kami-engine beside this repo) and retry.")))

;; --- 1. emit the WGSL kernel string (the @compute stage the GPU would run) ----------------
(let [wgsl (str (pc/cartpole-step-emit) "\n")]
  (io/make-parents (io/file wgsl-fixture))
  (spit wgsl-fixture wgsl)
  (println (format "  ✓ wrote %s (%d bytes)" wgsl-fixture (count wgsl))))

;; --- 2. compute the math golden (fixed input → SHA-256 of the output state) ----------------
(let [{:keys [state action cfg]} (cm/canonical-input)
      out     (cm/canonical-step)
      golden  {"input-state"           (vec state)
               "action"                action
               "cfg"                   {"cart_mass"        (:cart-mass cfg)
                                       "pole_mass"        (:pole-mass cfg)
                                       "pole_half_length" (:pole-half-length cfg)
                                       "gravity"          (:gravity cfg)
                                       "force_mag"        (:force-mag cfg)
                                       "dt"               (:dt cfg)}
               "expected-output-state" (mapv #(Double/parseDouble (format "%.12g" (double %))) out)
               "expected-output-hash"  (cm/output-hash out)
               "hash-algorithm"        "sha256"
               "hash-encoding"         "hex"
               "hash-precision"        "%.12g per component (12 significant digits; sub-ULP trig noise absorbed)"
               "source"                "kami.cartpole-math/canonical-step + kami.physics-compute/cartpole-step-emit"
               "note"                  (str "Phase 3.1 (ADR-2607010930): pins the cartpole semi-implicit Euler "
                                            "math (CLJC mirror) and the emitted WGSL string. Regenerate with "
                                            "`bb gen-compute-golden`; assert no drift with `git diff --exit-code`.")}]
  (io/make-parents (io/file golden-fixture))
  (spit golden-fixture (json/generate-string golden {:pretty true}))
  (println (format "  ✓ wrote %s" golden-fixture))
  (println (format "    input-state   %s" (pr-str (vec state))))
  (println (format "    output-state  %s" (pr-str (mapv #(Double/parseDouble (format "%.12g" (double %))) out))))
  (println (format "    output-hash   %s" (get golden "expected-output-hash"))))

(println "── compute-golden — kami.cartpole-math + kami.physics-compute single source ──")
(println "  regenerate: bb gen-compute-golden")
(println "  no-drift:   bb gen-compute-golden && git diff --exit-code")
