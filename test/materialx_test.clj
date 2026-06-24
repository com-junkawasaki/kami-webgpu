(ns materialx-test
  "Golden tests for kami.materialx — the MaterialX (XML) hiccup. They pin hiccup→XML: attributes,
   self-closing leaves, nested-element indentation, the <?xml?> declaration and <materialx> root, and
   the value helper. MaterialX is XML so this is hiccup in its original form."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [kami.materialx :as mx]
            [kami.xml :as xml]))

(deftest hiccup-to-xml
  (is (= "<input name=\"value\" type=\"color3\" value=\"1, 0, 0\" />"
         (xml/xml [:input {:name "value" :type "color3" :value (mx/value [1 0 0])}]))
      "self-closing leaf, value vector → comma-joined")
  (is (= "<output name=\"out\" type=\"color3\" nodename=\"c\" />"
         (xml/xml [:output {:name "out" :type "color3" :nodename "c"}])))
  (is (= "<nodegraph name=\"NG\">\n  <output name=\"o\" type=\"color3\" />\n</nodegraph>"
         (xml/xml [:nodegraph {:name "NG"} [:output {:name "o" :type "color3"}]]))
      "nested element indents"))

(deftest a-material-document
  (let [src (mx/materialx {:version "1.38"}
              [:nodegraph {:name "NG_red"}
               [:constant {:name "c1" :type "color3"}
                [:input {:name "value" :type "color3" :value (mx/value [1 0 0])}]]
               [:output {:name "out" :type "color3" :nodename "c1"}]]
              [:surfacematerial {:name "Mred" :type "material"}
               [:input {:name "surfaceshader" :type "surfaceshader" :nodename "SR_red"}]])]
    (is (str/starts-with? src "<?xml version=\"1.0\"?>\n<materialx version=\"1.38\">"))
    (is (str/includes? src "  <nodegraph name=\"NG_red\">"))
    (is (str/includes? src "      <input name=\"value\" type=\"color3\" value=\"1, 0, 0\" />"))
    (is (str/ends-with? src "</materialx>"))))

(let [{:keys [fail error]} (run-tests 'materialx-test)]
  (when (pos? (+ fail error))
    (throw (ex-info "materialx tests failed" {:fail fail :error error}))))
