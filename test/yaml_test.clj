(ns yaml-test
  "Edge-case tests for kami.yaml — the shared block YAML emitter under kami.ocio. They pin scalar
   quoting (so a string that looks like a number/bool/null stays a STRING), block mappings/sequences,
   nested structure, and !<tag> values — then prove type fidelity by parsing back with clj-yaml
   (clj-native round-trip, no Python). Guards the colour-config foundation."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [clj-yaml.core :as y]
            [kami.yaml :as yaml]))

(deftest quoting-keeps-type
  ;; the bug this fixes: "1.5" was emitted bare → parsed back as the number 1.5.
  (is (= "k: \"1.5\""   (yaml/yaml {:k "1.5"}))  "numeric-looking string is quoted")
  (is (= "k: \"42\""    (yaml/yaml {:k "42"})))
  (is (= "k: \"1e3\""   (yaml/yaml {:k "1e3"}))  "scientific")
  (is (= "k: \"0xff\""  (yaml/yaml {:k "0xff"})) "hex literal")
  (is (= "k: \"true\""  (yaml/yaml {:k "true"})) "boolean word")
  (is (= "k: v1.2.3"    (yaml/yaml {:k "v1.2.3"})) "non-numeric stays plain")
  (is (= "k: plain"     (yaml/yaml {:k "plain"})) "plain word unquoted")
  (is (= "k: 42"        (yaml/yaml {:k 42}))      "an actual number stays bare"))

(deftest round-trips-keep-string-type
  (doseq [s ["1.5" "42" "1e3" "0xff" "true" "null" "yes" "3.14"]]
    (let [parsed (y/parse-string (yaml/yaml {:k s}))]
      (is (= s (:k parsed)) (str "\"" s "\" round-trips as a string, not coerced")))))

(deftest structure
  (is (= "roles:\n  default: raw" (yaml/yaml {:roles {:default "raw"}})) "nested mapping")
  (is (= "xs:\n  - a\n  - b"      (yaml/yaml {:xs ["a" "b"]}))           "block sequence")
  (is (= "t: !<View>\n  name: x" (yaml/yaml {:t (yaml/ytag "View" {:name "x"})})) "tagged block"))

(let [{:keys [fail error]} (run-tests 'yaml-test)]
  (when (pos? (+ fail error))
    (throw (ex-info "yaml tests failed" {:fail fail :error error}))))
