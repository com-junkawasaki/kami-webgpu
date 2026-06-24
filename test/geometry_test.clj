(ns geometry-test
  "JVM golden tests for kami.webgpu.geometry — the canonical cross-platform mesh source. These
   pin vertex/index counts, watertight index bounds, the sphere's radius invariant, and
   determinism, so the native renderer can match this fixture instead of re-deriving geometry."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [kami.webgpu.geometry :as g]))

(defn- mag [[x y z]] (Math/sqrt (+ (* x x) (* y y) (* z z))))

(defn- well-formed?
  "Every index is in range and the index list is whole triangles."
  [{:keys [positions normals indices]}]
  (and (= (count positions) (count normals))
       (zero? (mod (count indices) 3))
       (every? #(< -1 % (count positions)) indices)))

(deftest plane-is-a-quad
  (let [m (g/plane 10 4)]
    (is (= 4 (count (:positions m))))
    (is (= 2 (g/tri-count m)))
    (is (well-formed? m))
    (is (every? #(= 0 (nth % 1)) (:positions m)) "plane lies in y=0")))

(deftest box-has-24-verts-12-tris
  (let [m (g/box 2 2 2)]
    (is (= 24 (count (:positions m))))
    (is (= 12 (g/tri-count m)))
    (is (well-formed? m))
    (is (every? #(<= (mag %) (+ 1e-6 (Math/sqrt 3))) (:positions m)) "corners within the box")))

(deftest sphere-positions-lie-on-the-radius
  (let [r 2.5 m (g/sphere r 8 12)]
    (is (= (* 9 13) (count (:positions m))) "(rings+1)·(sectors+1) verts")
    (is (= (* 8 12 2) (g/tri-count m)) "rings·sectors·2 triangles")
    (is (well-formed? m))
    (is (every? #(< (Math/abs (- (mag %) r)) 1e-4) (:positions m))
        "every sphere vertex is exactly r from the centre")))

(deftest cylinder-is-watertight
  (let [m (g/cylinder 1.0 3.0 16)]
    (is (well-formed? m))
    (is (pos? (g/tri-count m)))
    (is (every? #(<= (Math/abs (nth % 1)) (+ 1e-6 1.5)) (:positions m)) "within ±h/2 in y")))

(deftest generators-are-deterministic
  (is (= (g/sphere 2 6 9) (g/sphere 2 6 9)))
  (is (= (g/box 1 2 3) (g/box 1 2 3)))
  (is (= (g/cylinder 1 2 8) (g/cylinder 1 2 8))))

(defn- golden-json [{:keys [positions normals indices]}]
  (let [verts (vec (mapcat into positions normals))]
    (str "{\"verts\":[" (str/join "," verts) "],\"indices\":[" (str/join "," indices) "]}\n")))

(deftest meshes-match-the-cross-platform-golden
  ;; The committed fixtures/*-golden.json are the canonical meshes the native renderer
  ;; (kami-webgpu-rs) also asserts against — so a divergence in EITHER language fails its test
  ;; instead of drifting silently. This locks the CLJ side to those fixtures.
  (is (= (golden-json (g/box 1 1 1))          (slurp "fixtures/box-golden.json"))
      "CLJC box(1,1,1) must equal the committed golden")
  (is (= (golden-json (g/sphere 1.0 4 6))     (slurp "fixtures/sphere-golden.json"))
      "CLJC sphere(1,4,6) must equal the committed golden")
  (is (= (golden-json (g/cylinder 1.0 2.0 6)) (slurp "fixtures/cylinder-golden.json"))
      "CLJC cylinder(1,2,6) must equal the committed golden"))

(let [{:keys [fail error]} (run-tests 'geometry-test)]
  (when (pos? (+ fail error))
    (throw (ex-info "geometry tests failed" {:fail fail :error error}))))
