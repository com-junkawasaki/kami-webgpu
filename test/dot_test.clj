(ns dot-test
  "Golden tests for kami.dot — the GraphViz DOT hiccup. They pin node/edge default attrs, graph attrs,
   nodes with attrs, directed/undirected edges, attr value quoting (string vs. bare id), subgraph
   nesting, and a whole digraph. The real `dot` binary renders the same output in `bb gate`."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [kami.dot :as d]))

(deftest statements
  (is (= "node [shape=box];"          (d/stmt [:node {:shape :box}])) "bare keyword value")
  (is (= "a [label=\"Start\"];"        (d/stmt [:n :a {:label "Start"}])) "string value quoted")
  (is (= "a -> b [label=\"go\"];"      (d/stmt [:-> :a :b {:label "go"}])) "directed edge")
  (is (= "a -- b;"                    (d/stmt [:-- :a :b])) "undirected edge, no attrs")
  (is (= "rankdir=\"LR\";"             (d/stmt [:graph-attr {:rankdir "LR"}])))
  (is (= "subgraph cluster_0 {\n  a -> b;\n}"
         (d/stmt [:subgraph :cluster_0 [:-> :a :b]])) "subgraph nesting")
  (is (= "a [label=\"C:\\\\new\"];" (d/stmt [:n :a {:label "C:\\new"}]))
      "backslash escaped (else Graphviz reads \\n as a line break)"))

(deftest a-digraph-compiles
  (let [src (d/dot :digraph :G
              [:graph-attr {:rankdir "LR"}]
              [:node {:shape :box}]
              [:n :a {:label "Start"}]
              [:n :b {:shape :circle}]
              [:-> :a :b {:label "go"}]
              [:-> :b :c])]
    (is (str/starts-with? src "digraph G {\n  rankdir=\"LR\";"))
    (is (str/includes? src "  node [shape=box];"))
    (is (str/includes? src "  a [label=\"Start\"];"))
    (is (str/includes? src "  a -> b [label=\"go\"];"))
    (is (str/ends-with? src "\n}"))
    (is (= src (apply d/dot :digraph :G
                      [[:graph-attr {:rankdir "LR"}]
                       [:node {:shape :box}]
                       [:n :a {:label "Start"}]
                       [:n :b {:shape :circle}]
                       [:-> :a :b {:label "go"}]
                       [:-> :b :c]]))
        "deterministic")))

(let [{:keys [fail error]} (run-tests 'dot-test)]
  (when (pos? (+ fail error))
    (throw (ex-info "dot tests failed" {:fail fail :error error}))))
