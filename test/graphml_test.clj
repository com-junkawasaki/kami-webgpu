(ns graphml-test
  "Golden tests for kami.graphml — the GraphML interchange hiccup (built on kami.xml). They pin <key>
   attribute declarations (incl. keyword values rendered without a leading colon), nodes with <data>,
   self-closing empty nodes, edges, the graphml/graph wrapping, and the <?xml?> declaration. xmllint
   validates the same output for real in `bb gate`."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [kami.graphml :as gm]))

(deftest a-graph
  (let [src (gm/graphml {:id "G" :edgedefault :directed}
              [:key {:id "d0" :for :node :attr.name "label" :attr.type :string}]
              [:key {:id "d1" :for :edge :attr.name "weight" :attr.type :double}]
              [:node :a [:data "d0" "Start"]]
              [:node :b]
              [:edge :a :b [:data "d1" "1.5"]])]
    (is (str/starts-with? src "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<graphml xmlns="))
    (is (str/includes? src "<key id=\"d0\" for=\"node\" attr.name=\"label\" attr.type=\"string\" />")
        "keyword attr values render without a leading colon")
    (is (str/includes? src "<graph id=\"G\" edgedefault=\"directed\">"))
    (is (str/includes? src "<node id=\"a\">"))
    (is (str/includes? src "<data key=\"d0\">"))
    (is (str/includes? src "<node id=\"b\" />") "empty node self-closes")
    (is (str/includes? src "<edge source=\"a\" target=\"b\">"))
    (is (str/ends-with? src "</graphml>"))))

(let [{:keys [fail error]} (run-tests 'graphml-test)]
  (when (pos? (+ fail error))
    (throw (ex-info "graphml tests failed" {:fail fail :error error}))))
