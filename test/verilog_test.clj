(ns verilog-test
  "Golden tests for kami.verilog — the RTL hiccup. They pin that an EDN module compiles to the
   Verilog-2001 a design expects: ANSI ports with widths, wire/reg decls, continuous assign with a
   ternary, nonblocking always blocks with edge sensitivity + if/else, concat/bit-select, kebab→snake
   idents, and module instantiation. Expressions ride the shared kami.expr core, so this also guards
   that the algebra reused by kami.wgsl/kami.scad renders correctly under Verilog conventions."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [kami.verilog :as v]))

(deftest expressions
  (is (= "(a & b)"        (v/expr [:& :a :b])))
  (is (= "(sel ? a : b)"  (v/expr [:? :sel :a :b])) "ternary special")
  (is (= "{a, b, c[3:0]}" (v/expr [:cat :a :b [:slice :c 3 0]])) "concat + part-select")
  (is (= "q[0]"           (v/expr [:bit :q 0])))
  (is (= "8'hFF"          (v/expr "8'hFF")) "bit-literal passes through as a string")
  (is (= "data_in"        (v/expr :data-in)) "kebab→snake"))

(deftest statements
  (is (= "wire [7:0] y;"  (v/stmt [:wire :y 8])))
  (is (= "reg [3:0] q;"   (v/stmt [:reg :q 4])))
  (is (= "assign y = (sel ? a : b);" (v/stmt [:assign :y [:? :sel :a :b]])))
  (is (= "q <= (q + 1);"  (v/stmt [:<= :q [:+ :q 1]])) "nonblocking")
  (is (= "always @(posedge clk) q <= d;"
         (v/stmt [:always [:posedge :clk] [:<= :q :d]])) "single-stmt always, no begin/end"))

(deftest a-counter-compiles
  (let [src (v/module :counter
              [[:input :clk] [:input :rst] [:output :reg :q 4]]
              [:always [[:posedge :clk] [:posedge :rst]]
               [:if :rst
                [[:<= :q 0]]
                [[:<= :q [:+ :q 1]]]]])]
    (is (str/includes? src "module counter ("))
    (is (str/includes? src "output reg [3:0] q"))
    (is (str/includes? src "always @(posedge clk or posedge rst)") "multi-edge sensitivity")
    (is (str/includes? src "if (rst) q <= 0;"))
    (is (str/includes? src "else q <= (q + 1);"))
    (is (str/includes? src "endmodule"))
    (is (= src (apply v/module :counter
                      [[:input :clk] [:input :rst] [:output :reg :q 4]]
                      [[:always [[:posedge :clk] [:posedge :rst]]
                        [:if :rst
                         [[:<= :q 0]]
                         [[:<= :q [:+ :q 1]]]]]]))
        "deterministic")))

(deftest instantiation
  (is (= "dff u1 (\n  .clk(clk),\n  .d(d),\n  .q(q)\n);"
         (v/stmt [:inst :dff :u1 {:clk :clk :d :d :q :q}]))
      "named port connections, map order preserved"))

(let [{:keys [fail error]} (run-tests 'verilog-test)]
  (when (pos? (+ fail error))
    (throw (ex-info "verilog tests failed" {:fail fail :error error}))))
