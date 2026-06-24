;; gen_wgsl.clj — single-source the lit + shadow shaders across web + native.
;;
;; kami.shaders/{lit-shader,shadow-shader} (EDN AST → kami.wgsl) are the ONE source. `--write` emits
;; the canonical WGSL to fixtures/*.wgsl (committed beside the geometry goldens) AND, when the native
;; crate is co-located, to kami-engine/kami-webgpu-rs/src/*.wgsl (which native `include_str!`s). The
;; parity check confirms the native files match kami.shaders — so the web (kami.webgpu) and native
;; (kami-webgpu-rs) renderers can't silently diverge.
;;
;;   bb gen-wgsl          # write the .wgsl files + report native parity
;;   bb wgsl-parity       # report parity only          --strict → throw on drift
(require '[kami.shaders :as sh]
         '[clojure.string :as str]
         '[clojure.java.io :as io])

(def native-root "../kami-engine")
(def shaders [{:name "lit"    :wgsl (str (sh/lit-shader) "\n")
               :fixture "fixtures/lit-shader.wgsl"    :native "../kami-engine/kami-webgpu-rs/src/lit_shader.wgsl"}
              {:name "shadow" :wgsl (str (sh/shadow-shader) "\n")
               :fixture "fixtures/shadow-shader.wgsl" :native "../kami-engine/kami-webgpu-rs/src/shadow_shader.wgsl"}])

(defn- canon [s] (str/replace s #"[\s()]" ""))     ;; token stream (ws + grouping/call parens stripped)
(def co-located? (.exists (io/file native-root)))

(when (some #{"--write"} *command-line-args*)
  (doseq [{:keys [wgsl fixture native]} shaders]
    (io/make-parents (io/file fixture))
    (spit fixture wgsl)
    (println (format "wrote %s — %d bytes" fixture (count wgsl)))
    (when co-located?
      (spit native wgsl)
      (println (format "wrote %s" native)))))

(println "── lit + shadow shaders — web (kami.shaders) ↔ native (kami-webgpu-rs) single source ──")
(if-not co-located?
  (println "  ⊘ kami-engine not co-located — skip native parity (web canonical written)")
  (let [results (for [{:keys [name wgsl native]} shaders]
                  (let [f (io/file native)]
                    {:name name :exists (.exists f)
                     :match (and (.exists f) (= (canon (slurp f)) (canon wgsl)))}))]
    (doseq [{:keys [name exists match]} results]
      (cond
        (not exists) (println (format "  ⊘ %s: native .wgsl not generated yet (run bb gen-wgsl)" name))
        match        (println (format "  ✓ %s: native .wgsl is token-equivalent to kami.shaders — single source holds" name))
        :else        (println (format "  ✗ %s: DRIFT — native .wgsl differs from kami.shaders" name))))
    (when (and (some #{"--strict"} *command-line-args*) (not (every? :match results)))
      (throw (ex-info "lit/shadow shader DRIFT (native ≠ kami.shaders)" {})))))
