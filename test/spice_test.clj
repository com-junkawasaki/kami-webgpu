(ns spice-test
  "Golden tests for kami.spice — the SPICE netlist hiccup. They pin that EDN statements compile to deck
   lines: element letter+name prefixing, :gnd→0, source specs (DC / SIN(...)), a trailing parameter
   map (W=…/L=…), a .model card, directives (.tran/.op), and a whole RC + transient deck."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [kami.spice :as sp]))

(deftest elements-and-sources
  (is (= "R1 in out 1k"          (sp/stmt [:r 1 :in :out "1k"])))
  (is (= "C1 out 0 100n"         (sp/stmt [:c 1 :out :gnd "100n"])) ":gnd → node 0")
  (is (= "V1 in 0 DC 5"          (sp/stmt [:v 1 :in :gnd [:dc 5]])) "DC source spec")
  (is (= "V2 n 0 SIN(0 1 1k)"    (sp/stmt [:v 2 :n :gnd [:sin 0 1 "1k"]])) "SIN(...) source")
  (is (= "M1 d g s b NMOS W=1u L=0.18u"
         (sp/stmt [:m 1 :d :g :s :b "NMOS" {:W "1u" :L "0.18u"}])) "model name + param map"))

(deftest directives-and-model
  (is (= ".tran 1u 1m"  (sp/stmt [:tran "1u" "1m"])))
  (is (= ".op"          (sp/stmt [:op])))
  (is (= ".end"         (sp/stmt [:end])))
  (is (= ".model NMOS NMOS (vto=0.7 kp=120u)"
         (sp/stmt [:model :NMOS :NMOS {:vto "0.7" :kp "120u"}]))))

(deftest an-rc-deck-compiles
  (let [src (sp/netlist "RC low-pass"
              [:v 1 :in :gnd [:dc 5]]
              [:r 1 :in :out "1k"]
              [:c 1 :out :gnd "100n"]
              [:tran "1u" "1m"]
              [:end])]
    (is (= (str "* RC low-pass\n"
                "V1 in 0 DC 5\n"
                "R1 in out 1k\n"
                "C1 out 0 100n\n"
                ".tran 1u 1m\n"
                ".end")
           src))))

(let [{:keys [fail error]} (run-tests 'spice-test)]
  (when (pos? (+ fail error))
    (throw (ex-info "spice tests failed" {:fail fail :error error}))))
