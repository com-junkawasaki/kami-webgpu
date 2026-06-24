(ns shader-test
  "Safety gate for adopting kami.wgsl in the live renderer. The shader runs via WebGPU, which can't
   be screenshot headlessly, so we instead prove the GENERATED fragment is token-equivalent to the
   hand-written WGSL that shipped: identical token stream once whitespace and (grouping/call)
   parentheses are stripped. If kami.shaders/lit-fs ever drifts from the lighting that was verified
   on-screen, this fails — so the EDN can drive the renderer without a visual re-check."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [kami.shaders :as sh]))

;; the exact fragment that shipped in kami.webgpu (the on-screen-verified lighting).
(def golden-fs
  "@fragment
fn fs(i: VO) -> @location(0) vec4<f32> {
  let N = normalize(i.n);
  let L = normalize(-g.sun_dir.xyz);
  let eye = vec3<f32>(g.sun_dir.w, g.sun_col.w, g.sky.w);
  let V = normalize(eye - i.wpos);
  let H = normalize(L + V);
  let ndl = max(dot(N, L), 0.0);
  let metallic  = clamp(i.mat.x, 0.0, 1.0);
  let rough     = clamp(i.mat.y, 0.04, 1.0);
  let emissive  = i.mat.z;
  let amb = mix(g.light_a.rgb, g.sky.rgb*g.light_a.w, N.y*0.5+0.5);
  let shininess = mix(g.light_c.x, g.light_c.y, 1.0 - rough);
  let specStr   = mix(g.light_b.x, g.light_b.y, metallic);
  let specTint  = mix(vec3<f32>(1.0), i.col, metallic);
  let spec = pow(max(dot(N, H), 0.0), shininess) * specStr;
  let rim  = pow(1.0 - max(dot(N, V), 0.0), g.light_b.w) * g.light_b.z;
  let sh = shadow(i.wpos, ndl);
  var c = i.col * (amb + ndl * g.sun_col.rgb * g.light_c.z * (1.0 - metallic*g.light_c.w) * sh)
        + specTint * g.sun_col.rgb * spec * sh
        + g.sky.rgb * rim
        + i.col * emissive;
  c = c / (c + vec3<f32>(1.0));
  c = pow(c, vec3<f32>(1.0/g.light_d.x));
  return vec4<f32>(c, 1.0);
}")

;; token stream sans whitespace and parens (call + grouping). Equal canon ⇒ same operations/operands
;; in the same order ⇒ functionally identical given kami.wgsl's tested operator precedence.
(defn- canon [s] (str/replace s #"[\s()]" ""))

(deftest lit-fs-matches-the-shipped-shader
  (is (= (canon golden-fs) (canon (sh/lit-fs)))
      "kami.wgsl-generated fragment is token-equivalent to the hand-written, on-screen-verified WGSL"))

(let [{:keys [fail error]} (run-tests 'shader-test)]
  (when (pos? (+ fail error))
    (throw (ex-info "shader gate failed" {:fail fail :error error}))))
