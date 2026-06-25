(ns kami.sprite-gpu
  "2D as GPU instanced quads — the bridge that moves the sprite renderer off Canvas2D and onto the
   GPU pipeline IR (kami.gpu). Every sprite primitive (circle / ellipse / rect / arc) becomes one
   instanced quad carrying its shape + transform + colour; a 2D-SDF fragment shader rasterises the
   shape on the GPU. So 2D renders through the same pipeline as 3D — identically on WebGPU, WebGL2,
   Metal, and consoles — instead of through Canvas2D's divergent immediate-mode path. `.cljc`.

   In a quad's normalised [-1,1] uv: circle and ellipse are the SAME SDF (length(uv)-1) — the quad's
   :size makes it elliptical; rect is the box SDF; arc carries inner/angle params for crescents."
  (:require [kami.wgsl :as w]))

;; shape → the integer the SDF shader switches on
(def shapes {:circle 0 :ellipse 0 :rect 1 :arc 2})

(defn- rgba [fill]
  (let [v (vec (or fill [1.0 1.0 1.0]))]
    (if (= 4 (count v)) v (conj v 1.0))))

(defn prim->quad
  "One sprite primitive `[kind props]` at entity centre [ex ey] → a GPU quad instance.
   props use the sprite2d vocabulary: :dx/:dy offset, :r (circle), :rx/:ry (ellipse/arc), :w/:h
   (rect), :fill colour. Returns {:pos :size :rot :shape :color} (size = half-extents)."
  [[ex ey] [kind {:keys [dx dy r rx ry w h rot fill]}]]
  (let [[sw sh] (case kind
                  :circle  [(or r 1.0)  (or r 1.0)]
                  :ellipse [(or rx 1.0) (or ry 1.0)]
                  :rect    [(or w 1.0)  (or h 1.0)]
                  :arc     [(or rx r 1.0) (or ry r 1.0)]
                  [(or r rx 1.0) (or r ry 1.0)])]
    {:pos   [(+ ex (or dx 0.0)) (+ ey (or dy 0.0))]
     :size  [sw sh]
     :rot   (or rot 0.0)
     :shape (get shapes kind 0)
     :color (rgba fill)}))

(defn prims->quads
  "A sprite recipe (vector of `[kind props]` primitives) at entity centre → a vector of GPU quad
   instances. Painter order is preserved (later primitives draw on top)."
  [center prims]
  (mapv #(prim->quad center %) prims))

(defn draw-ops->quads
  "Flatten a kami.sprite2d.layout draw-list (ops {:sprite [prims…] :sx :sy}) into one flat quad
   instance array for a single instanced draw — the whole 2D frame as GPU sprite instances."
  [ops]
  (into [] (mapcat (fn [{:keys [sprite sx sy]}] (prims->quads [sx sy] sprite)) ops)))

(defn pack-instances
  "Pack quad instances into a flat f32 array for the GPU vertex buffer, 8 floats per instance:
   pos.xy, size.xy, rot, shape, color.rg (… plus a 2nd row for color.ba in the real layout).
   Returns a vector of floats — the canonical 2D-sprite instance layout (12 floats/instance:
   pos2 size2 rot1 shape1 color4 + pad2)."
  [quads]
  (into [] (mapcat (fn [{:keys [pos size rot shape color]}]
                     (map double (concat pos size [rot shape] color [0.0 0.0])))   ;; all f32 for the GPU
                   quads)))

;; ── the 2D-SDF sprite shader (instanced quads), generated from kami.wgsl EDN ─────────────────────
;; one instanced quad per primitive; the fragment evaluates the shape's signed-distance field for an
;; anti-aliased fill. Pure raster (no compute/storage) → runs on WebGPU AND WebGL2 (kami.gpu has no
;; :requires on this pass). vertex_index 0..5 builds the quad; instance attrs carry pos/size/rot/
;; shape/color in screen pixels; :viewport maps to clip space.
(defn sprite-sdf-shader []
  (w/shader
   (w/struct* :U [[:viewport [:vec2 :f32]] [:_p0 [:vec2 :f32]]])
   (w/binding* {:group 0 :binding 0 :space :uniform} :u :U)
   (w/struct* :VO [[:clip [:vec4 :f32] {:builtin :position}]
                   [:uv [:vec2 :f32] {:location 0}]
                   [:shape :f32 {:location 1}]
                   [:color [:vec4 :f32] {:location 2}]])
   (w/func :vs {:stage :vertex
                :params [[:vid :u32 {:builtin :vertex-index}]
                         [:ipos [:vec2 :f32] {:location 0}] [:isize [:vec2 :f32] {:location 1}]
                         [:irot :f32 {:location 2}] [:ishape :f32 {:location 3}]
                         [:icolor [:vec4 :f32] {:location 4}]]
                :ret :VO}
           "var corners = array<vec2<f32>, 6>(vec2<f32>(-1.0,-1.0), vec2<f32>(1.0,-1.0), vec2<f32>(-1.0,1.0), vec2<f32>(-1.0,1.0), vec2<f32>(1.0,-1.0), vec2<f32>(1.0,1.0))"
           [:let :q "corners[vid]"]
           [:let :c [:cos :irot]] [:let :s [:sin :irot]]
           [:let :scaled [:* :q :isize]]
           [:let :rotated [:vec2 [:- [:* :scaled.x :c] [:* :scaled.y :s]] [:+ [:* :scaled.x :s] [:* :scaled.y :c]]]]
           [:let :px [:+ :ipos :rotated]]
           [:let :ndc [:- [:* [:/ :px :u.viewport] 2.0] 1.0]]
           [:decl :o :VO]
           [:set :o.clip [:vec4 :ndc.x [:- :ndc.y] 0.0 1.0]]
           [:set :o.uv :q] [:set :o.shape :ishape] [:set :o.color :icolor]
           [:return :o])
   (w/func :fs {:stage :fragment :params [[:i :VO]] :ret [:loc 0 [:vec4 :f32]]}
           [:var :d [:- [:length :i.uv] 1.0]]                       ;; circle/ellipse (shape 0)
           [:if [:> :i.shape 0.5]                                   ;; shape 1 = rect (box SDF)
            [[:set :d [:- [:max [:abs :i.uv.x] [:abs :i.uv.y]] 1.0]]]]
           [:let :aa [:fwidth :d]]
           [:let :cov [:- 1.0 [:smoothstep [:- :aa] :aa :d]]]       ;; anti-aliased coverage
           [:if [:<= :cov 0.0] ["discard"]]
           [:return [:vec4 :i.color.rgb [:* :i.color.a :cov]]])))

