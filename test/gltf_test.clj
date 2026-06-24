(ns gltf-test
  "Golden tests for kami.gltf — the glTF 2.0 hiccup. They pin the injected asset block, the node and
   material constructors, and the JSON shape. gltf-validator validates the same output in `bb gate`."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [kami.gltf :as g]))

(deftest constructors
  (is (= {:name "red" :pbrMetallicRoughness {:baseColorFactor [1 0 0 1]}}
         (g/material "red" [1 0 0 1])))
  (is (= {:name "root" :mesh 0} (g/node {:name "root" :mesh 0 :translation nil}))
      "nil optional fields are dropped"))

(deftest a-document-injects-asset
  (let [src (g/gltf {:generator "kami"}
                    {:scene 0
                     :scenes [{:nodes [0]}]
                     :nodes [(g/node {:name "root" :mesh 0 :translation [0 1 0]})]
                     :meshes [{:name "tri" :primitives [{:attributes {:POSITION 0} :material 0}]}]
                     :materials [(g/material "red" [1 0 0 1])]})]
    (is (str/includes? src "\"asset\": {\n    \"version\": \"2.0\",\n    \"generator\": \"kami\"\n  }"))
    (is (str/includes? src "\"baseColorFactor\": [\n"))
    (is (str/includes? src "\"POSITION\": 0"))
    (is (str/starts-with? src "{\n  \"asset\"") "pretty JSON, asset first")))

(let [{:keys [fail error]} (run-tests 'gltf-test)]
  (when (pos? (+ fail error))
    (throw (ex-info "gltf tests failed" {:fail fail :error error}))))
