(ns expr-test
  "Dedicated tests for kami.expr — the shared infix core under kami.wgsl/scad/verilog (previously only
   exercised through those). They pin the algebra directly: variadic/unary operators, nesting & paren
   grouping, function calls, bitwise ops (incl. the keyword-built :^/:~), single-element-vector idents,
   leaf passthrough, and the :ident/:num/:call/:special customisation hooks."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [kami.expr :as kx]))

(deftest operators-and-leaves
  (is (= "(a + b + c)"      (kx/compile [:+ :a :b :c])) "variadic")
  (is (= "(-x)"             (kx/compile [:- :x]))       "unary")
  (is (= "((a + b) * c)"    (kx/compile [:* [:+ :a :b] :c])) "nesting + parens")
  (is (= "dot(n, l)"        (kx/compile [:dot :n :l]))  "function call (default :call)")
  (is (= "foo"              (kx/compile [:foo]))        "single-element vector → ident")
  (is (= "x"                (kx/compile :x))            "keyword leaf")
  (is (= "42"               (kx/compile 42))            "number leaf")
  (is (= "raw"              (kx/compile "raw"))         "string passthrough (escape hatch)"))

(deftest bitwise-and-logical
  (is (= "(p && q)" (kx/compile [:&& :p :q])))
  (is (= "(a >> 2)" (kx/compile [:>> :a 2])))
  (is (= "(a ^ b)"  (kx/compile [(keyword "^") :a :b])) "xor — built via keyword (bb reader)")
  (is (= "(~m)"     (kx/compile [(keyword "~") :m]))    "bit-not unary"))

(deftest customisation-hooks
  (let [snake {:ident (fn [k] (str/replace (name k) "-" "_"))
               :num   (fn [n] (str n ".0"))}]
    (is (= "(sun_dir * 2.0)"      (kx/compile snake [:* :sun-dir 2])) ":ident kebab→snake, :num float")
    (is (= "max(dot(N, L), 0.0)"  (kx/compile snake [:max [:dot :N :L] 0])) "hooks recurse"))
  (let [ctor {:call (fn [op args] (str (name op) "<f32>(" (str/join ", " args) ")"))}]
    (is (= "vec3<f32>(a, b, c)" (kx/compile ctor [:vec3 :a :b :c])) ":call override (ctor-style)"))
  (let [special {:special (fn [op xs _go] (when (= op :i) (str (first xs))))}]   ;; raw passthrough
    (is (= "(7 + x)" (kx/compile special [:+ [:i 7] :x])) ":special consulted at every node")))

(let [{:keys [fail error]} (run-tests 'expr-test)]
  (when (pos? (+ fail error))
    (throw (ex-info "expr tests failed" {:fail fail :error error}))))
