(ns sprite-gpu-test
  "Tests for 2D-as-GPU-instances: the sprite-primitive → quad-instance converter, and that the
   2D-SDF sprite shader generates valid WGSL (validated by naga = wgpu's frontend, separately)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [kami.sprite-gpu :as sg]))

(deftest primitive-to-quad
  (is (= {:pos [10.0 20.0] :size [185 185] :rot 0.0 :shape 0 :color [0.3 0.2 0.1 1.0]}
         (sg/prim->quad [10.0 20.0] [:circle {:r 185 :fill [0.3 0.2 0.1]}])))
  (is (= 0 (:shape (sg/prim->quad [0 0] [:ellipse {:rx 380 :ry 340 :fill [0 0 0]}])))
      "ellipse shares the circle SDF; the quad :size makes it elliptical")
  (is (= [380 340] (:size (sg/prim->quad [0 0] [:ellipse {:rx 380 :ry 340 :fill [0 0 0]}]))))
  (is (= 1 (:shape (sg/prim->quad [0 0] [:rect {:w 10 :h 20 :fill [1 1 1]}]))) "rect = box SDF")
  (is (= [1 1 1 1.0] (:color (sg/prim->quad [0 0] [:circle {:r 1 :fill [1 1 1]}]))) "rgb→rgba"))

(deftest recipe-and-frame
  (let [gorilla [[:ellipse {:dx 0   :dy -40 :rx 380 :ry 340 :fill [0.13 0.11 0.10]}]
                 [:circle  {:dx -170 :dy 300 :r 185 :fill [0.34 0.21 0.18]}]]
        quads (sg/prims->quads [100 200] gorilla)]
    (is (= 2 (count quads)) "one quad per primitive, painter order preserved")
    (is (= [100 160]  (:pos (first quads)))  "centre + dx/dy (200 + -40)")
    (is (= [-70 500]  (:pos (second quads))) "centre + dx/dy (100-170, 200+300)")
    ;; a whole 2D frame (two entities) → one flat instance array
    (let [ops [{:sprite gorilla :sx 100 :sy 200} {:sprite [[:rect {:w 5 :h 5 :fill [1 0 0]}]] :sx 0 :sy 0}]
          flat (sg/draw-ops->quads ops)]
      (is (= 3 (count flat)) "all primitives across all entities → one instanced draw")
      (is (= 36 (count (sg/pack-instances flat))) "12 floats per instance, GPU-buffer-ready"))))

(let [{:keys [fail error]} (run-tests 'sprite-gpu-test)]
  (when (pos? (+ fail error))
    (throw (ex-info "sprite-gpu tests failed" {:fail fail :error error}))))
