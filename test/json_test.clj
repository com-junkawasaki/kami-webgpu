(ns json-test
  "Edge-case tests for kami.json — the shared JSON emitter under kami.otio/kami.gltf. They pin control-
   char escaping (\\\" \\\\ \\b \\f \\n \\r \\t), empty collections, nil/bool/number/keyword rendering,
   and nesting — then prove validity by parsing the output back with cheshire (clj-native round-trip,
   no external tool). Guards the foundation four formats depend on."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [cheshire.core :as cheshire]
            [kami.json :as json]))

(deftest escaping
  (is (= "\"he said \\\"hi\\\"\"" (json/json "he said \"hi\"")) "quotes escaped")
  (is (= "\"a\\\\b\""            (json/json "a\\b")) "backslash escaped")
  (is (= "\"x\\r\\n\\ty\""       (json/json "x\r\n\ty")) "CR/LF/tab escaped (CR was the bug)")
  (is (not (str/includes? (json/json "a\rb") "\r")) "no raw carriage return in output"))

(deftest scalars-and-empties
  (is (= "{}" (json/json {})))
  (is (= "[]" (json/json [])))
  (is (= "null"  (json/json nil)))
  (is (= "true"  (json/json true)))
  (is (= "\"k\"" (json/json :k)) "keyword → string"))

(deftest round-trips-through-cheshire
  ;; the strongest check: a real JSON parser must read back exactly what we wrote (incl. control chars).
  (doseq [v [{:a 1 :b [1 2.5 "x"]}
             {:s "tab\there, quote\" and back\\slash, CR\r LF\n"}
             {:nested {:deep {:n nil :t true :f false}} :arr [{} [] {:k "v"}]}]]
    (is (= (cheshire/parse-string (json/json v) true) v)
        (str "round-trips: " (pr-str v)))))

(let [{:keys [fail error]} (run-tests 'json-test)]
  (when (pos? (+ fail error))
    (throw (ex-info "json tests failed" {:fail fail :error error}))))
