(ns kami.scene2d
  "A whole 2D frame as GPU data — assembles the sky gradient params + every quad (entity sprites via
   kami.sprite2d.layout with the per-entity idle breathing/bob, fx particles + 7-segment text) so the
   WebGL2/WebGPU runtime draws the frame the way kami.sprite2d/draw-2d! did on Canvas2D, but through
   the GPU instanced pipeline. One EDN frame → sky pass + one instanced quad pass. `.cljc`.

   This is the assembly that replaces Canvas2D: the renderers (sprite-gpu quads, sky gradient, text)
   are all GPU + pixel-verified; scene2d wires them to the same layout draw-2d! used."
  (:require [kami.sprite-gpu :as sg]
            [kami.text :as txt]
            [kami.sprite2d.layout :as layout]))

(defn- rgba [c] (let [v (vec (or c [1 1 1]))] (if (= 4 (count v)) v (conj v 1.0))))

(defn sky-params
  "The fullscreen gradient colours from the scene's :render/sky (zenith→ground), as vec4s."
  [scene]
  (let [sky (:render/sky scene)]
    {:zenith (rgba (or (:zenith sky) [0.04 0.06 0.12]))
     :ground (rgba (or (:ground sky) [0.10 0.20 0.12]))}))

(defn- idle
  "Per-entity idle life: a tick-driven breathing scale + vertical bob, phase-desynced per entity —
   the same liveliness draw-2d! applied (gorilla heaves more; the player has a lighter bounce)."
  [tag tick sx sy]
  (let [amp (case tag "gorilla" 0.07 "player" 0.035 0.025)
        ph  (* 0.013 (+ sx sy))
        br  (+ 1.0 (* amp (#?(:clj Math/sin :cljs js/Math.sin) (+ (* tick 0.11) ph))))
        bob (* (if (= tag "gorilla") 5.0 3.0) (#?(:clj Math/sin :cljs js/Math.sin) (+ (* tick 0.09) ph)))]
    [br bob]))

(defn- op->quads
  "One sprite op → quads: per-part :anim (sprite-gpu) then the per-entity idle breathe+bob around
   the entity centre."
  [tick {:keys [tag sprite sx sy]}]
  (let [[br bob] (idle tag tick sx sy)]
    (mapv (fn [q]
            (let [[qx qy] (:pos q) [qw qh] (:size q)]
              (assoc q :pos  [(+ sx (* br (- qx sx))) (+ sy (* br (- qy sy)) bob)]
                       :size [(* qw br) (* qh br)])))
          (sg/prims->quads [sx sy] sprite tick (* 0.013 (+ sx sy))))))

(defn fx->quads
  "Screen-space juice → quads: particles are circles; floating text is 7-segment glyphs. fx are placed
   relative to the screen centre [cx cy] (kami.sprite2d's convention)."
  [[cx cy] fx]
  (into []
    (mapcat (fn [{:keys [kind ox oy color text size]}]
              (let [x (+ cx (or ox 0)) y (+ cy (or oy 0))]
                (case kind
                  :part [{:pos [x y] :size (let [s (or size 6)] [s s]) :rot 0.0 :shape 0 :color (rgba color)}]
                  :text (txt/text->quads (str text) [x y] (let [h (or size 14)] [(* h 0.6) h]) color)
                  [])))
            fx)))

(defn frame-quads
  "The whole 2D frame as GPU data: {:sky {:zenith :ground} :quads [instances…]} — sky gradient params
   plus every sprite/fx quad for one instanced draw. `tick` drives idle + :anim; `fx` is the juice."
  [scene snap fx tick W H]
  {:sky   (sky-params scene)
   :quads (into (into [] (mapcat #(op->quads tick %) (layout/draw-list scene snap W H)))
                (fx->quads [(/ W 2.0) (/ H 2.0)] fx))})
