(ns kami.shaders
  "Game shaders as data — the kami.wgsl EDN AST for the lit instanced renderer's fragment lighting.
   The lighting model (hemisphere ambient, Lambert, Blinn-Phong spec, Fresnel rim, PCF shadow,
   Reinhard tonemap, gamma) is authored as data, so it reads and forks like the scene, and ONE
   source generates the WGSL the web (kami.webgpu) runs — and, in time, the native kami-webgpu-rs
   path (parity by source). The struct/bindings/shadow/vertex preamble stays a template in
   kami.webgpu; this is the fragment a designer actually tunes. `.cljc` → browser + bb/JVM."
  (:require [kami.wgsl :as w]))

;; lighting inputs come from the G uniform (g.light_a..d pack the tunables) and the VO varying `i`.
(def lit-fs-body
  [[:let :N [:normalize :i.n]]
   [:let :L [:normalize [:- :g.sun-dir.xyz]]]
   [:let :eye [:vec3 :g.sun-dir.w :g.sun-col.w :g.sky.w]]
   [:let :V [:normalize [:- :eye :i.wpos]]]
   [:let :H [:normalize [:+ :L :V]]]
   [:let :ndl [:max [:dot :N :L] 0.0]]
   [:let :metallic [:clamp :i.mat.x 0.0 1.0]]
   [:let :rough [:clamp :i.mat.y 0.04 1.0]]
   [:let :emissive :i.mat.z]
   [:let :amb [:mix :g.light-a.rgb [:* :g.sky.rgb :g.light-a.w] [:+ [:* :N.y 0.5] 0.5]]]
   [:let :shininess [:mix :g.light-c.x :g.light-c.y [:- 1.0 :rough]]]
   [:let :specStr [:mix :g.light-b.x :g.light-b.y :metallic]]
   [:let :specTint [:mix [:vec3 1.0] :i.col :metallic]]
   [:let :spec [:* [:pow [:max [:dot :N :H] 0.0] :shininess] :specStr]]
   [:let :rim [:* [:pow [:- 1.0 [:max [:dot :N :V] 0.0]] :g.light-b.w] :g.light-b.z]]
   [:let :sh [:shadow :i.wpos :ndl]]
   [:var :c [:+ [:* :i.col [:+ :amb [:* :ndl :g.sun-col.rgb :g.light-c.z [:- 1.0 [:* :metallic :g.light-c.w]] :sh]]]
                [:* :specTint :g.sun-col.rgb :spec :sh]
                [:* :g.sky.rgb :rim]
                [:* :i.col :emissive]]]
   [:set :c [:/ :c [:+ :c [:vec3 1.0]]]]            ;; Reinhard tonemap
   [:set :c [:pow :c [:vec3 [:/ 1.0 :g.light-d.x]]]] ;; gamma
   [:return [:vec4 :c 1.0]]])

(defn lit-fs
  "The lit instanced renderer's fragment shader, generated from data."
  []
  (apply w/func :fs {:stage :fragment :params [[:i :VO]] :ret [:loc 0 [:vec4 :f32]]} lit-fs-body))
