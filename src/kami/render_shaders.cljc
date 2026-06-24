(ns kami.render-shaders
  "kami-render's native open-world shaders, as data (kami.wgsl EDN AST). These ship in the native
   Rust renderer (kami-render, used by the kami-app-{game} open-world games); EDN-ifying them makes
   the shaders forkable data and lets `bb gen-wgsl` + a parity gate keep the committed .wgsl files
   token-equivalent to this source. Ported incrementally, simplest first; each is gated against the
   shipping shader (token-equivalent ⇒ same program ⇒ renders identically). `.cljc`."
  (:require [kami.wgsl :as w]))

;; ── scene_character — procedural humanoid: per-vertex colour, model transform, sun + fog ──────────
(def character-U
  [[:viewProj :mat4x4f] [:model :mat4x4f]
   [:camPos :vec3f] [:_p0 :f32]
   [:sunDir :vec3f] [:_p1 :f32]
   [:sunColor :vec3f] [:fogDensity :f32]
   [:fogColor :vec3f] [:_p2 :f32]])

(defn scene-character []
  (w/shader
   (w/struct* :U character-U)
   (w/binding* {:group 0 :binding 0 :space :uniform} :u :U)
   (w/struct* :VIn [[:pos :vec3f {:location 0}] [:normal :vec3f {:location 1}] [:color :vec3f {:location 2}]])
   (w/struct* :VO [[:cp :vec4f {:builtin :position}] [:wp :vec3f {:location 0}]
                   [:n :vec3f {:location 1}] [:col :vec3f {:location 2}]])
   (apply w/func :vs {:stage :vertex :params [[:v :VIn]] :ret :VO}
          [[:decl :o :VO]
           [:let :wp [:. [:* :u.model [:vec4f :v.pos 1.0]] :xyz]]
           [:set :o.cp [:* :u.viewProj [:vec4f :wp 1.0]]]
           [:set :o.wp :wp]
           [:set :o.n [:normalize [:. [:* :u.model [:vec4f :v.normal 0.0]] :xyz]]]
           [:set :o.col :v.color]
           [:return :o]])
   (apply w/func :fs {:stage :fragment :params [[:v :VO]] :ret [:loc 0 :vec4f]}
          [[:let :NdotL [:max [:dot :v.n :u.sunDir] 0.0]]
           [:let :ambient [:vec3f 0.45 0.46 "0.50"]]   ;; "0.50" verbatim to match the shipped literal
           [:var :col [:* :v.col [:+ :ambient [:* :u.sunColor :NdotL 0.5]]]]
           [:let :d [:length [:- :v.wp :u.camPos]]]
           [:let :f [:- 1.0 [:exp [:* [:- :d] :u.fogDensity 2.0]]]]
           [:set :col [:mix :col :u.fogColor [:clamp :f 0.0 0.9]]]
           [:return [:vec4f :col 1.0]]])))
