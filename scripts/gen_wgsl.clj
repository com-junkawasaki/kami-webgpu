;; gen_wgsl.clj — single-source the lit shader across web + native.
;;
;; kami.shaders/lit-shader (the EDN AST → kami.wgsl) is the ONE source. This writes the canonical
;; WGSL to fixtures/lit-shader.wgsl (committed, beside the geometry goldens) and checks the native
;; copy (kami-engine/kami-webgpu-rs const SHADER) against it — so a divergence between the web
;; (kami.webgpu) and native (kami-webgpu-rs) renderers becomes a visible, tracked drift.
;;
;;   bb gen-wgsl          # write fixtures/lit-shader.wgsl + report native parity
;;   bb wgsl-parity       # report parity only (no write)        --strict → throw on drift
(require '[kami.shaders :as sh]
         '[clojure.string :as str]
         '[clojure.set :as set]
         '[clojure.java.io :as io])

(def canonical (str (sh/lit-shader) "\n"))
(def fixture    "fixtures/lit-shader.wgsl")
(def native-lib "../kami-engine/kami-webgpu-rs/src/lib.rs")

(defn- canon [s] (str/replace s #"[\s()]" ""))     ;; token stream (ws + grouping/call parens stripped)

(when (some #{"--write"} *command-line-args*)
  (io/make-parents (io/file fixture))
  (spit fixture canonical)
  (println (format "wrote %s — %d bytes (canonical lit shader from kami.shaders/lit-shader)"
                   fixture (count canonical))))

(println "── lit shader — web (kami.shaders) ↔ native (kami-webgpu-rs) single-source ──")
(let [f (io/file native-lib)]
  (if-not (.exists f)
    (println "  ⊘ kami-engine not co-located — skip native parity (web canonical written)")
    (let [m      (re-find #"(?s)const SHADER: &str = r#\"(.*?)\"#;" (slurp f))
          native (some-> m second)]
      (cond
        (nil? native) (println "  ⊘ couldn't locate native `const SHADER` in lib.rs")
        (= (canon native) (canon canonical))
        (println "  ✓ native const SHADER is token-equivalent to kami.shaders/lit-shader — single source holds")
        :else
        (let [nu (set (re-seq #"light_[a-d]" native))
              cu (set (re-seq #"light_[a-d]" canonical))
              miss (sort (set/difference cu nu))]
          (println "  ✗ DRIFT — native const SHADER differs from the web canonical:")
          (println "      web  uniforms:" (sort cu) "  native uniforms:" (sort nu))
          (when (seq miss) (println "      native is missing the tunable-lighting uniforms:" miss
                                    "→ native renders an older hardcoded lighting model"))
          (when (some #{"--strict"} *command-line-args*)
            (throw (ex-info "lit shader DRIFT (native ≠ kami.shaders)" {:missing miss}))))))))
