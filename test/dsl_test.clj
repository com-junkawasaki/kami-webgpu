(ns dsl-test
  "Golden tests for the data DSLs kami.re (regex hiccup) and kami.css (style hiccup): pin that an
   EDN form compiles to the expected pattern / CSS, including the real shapes used in the app
   (the worker's name sanitiser, the flow-card keyframes). .cljc compilers, so this guards the
   browser output too."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [kami.re :as re]
            [kami.css :as css]))

;; ── kami.re ──────────────────────────────────────────────────────────────────────────────
(deftest regex-forms
  (is (= "a\\.b"      (re/rx "a.b")) "literal metachars escaped")
  (is (= "\\d+"       (re/rx [:+ :digit])))
  (is (= "(?:ab)*"    (re/rx [:* [:seq "a" "b"]])) "multi-char repeat is grouped")
  (is (= "(?:a|b|c)"  (re/rx [:or "a" "b" "c"])))
  (is (= "[a-z0-9]"   (re/rx [:class :alpha "0-9"])))
  (is (= "\\w{2,4}"   (re/rx [:rep :word 2 4]))))

(deftest real-world-patterns
  ;; the leaderboard worker's name sanitiser: strip anything but word/space/-!?+.
  (is (= "[^\\w \\-!?+.]" (re/rx [:not :word \space \- \! \? \+ \.])))
  ;; …and it actually sanitises when compiled (drops *, ~, @ — keeps word/space/-/!)
  (is (= "Bob the-Gorilla!"
         (str/replace "Bob*~ the-Gorilla!@" (re/re [:not :word \space \- \! \? \+ \.]) ""))))

;; ── kami.css ─────────────────────────────────────────────────────────────────────────────
(deftest css-values
  (is (= "width: 100px;"          (str/trim (css/style {:width 100}))) "number → px")
  (is (= "opacity: 0.5;"          (str/trim (css/style {:opacity 0.5}))) "unitless prop")
  (is (= "padding: 12px 22px;"    (str/trim (css/style {:padding [12 22]}))) "vector = space-joined")
  (is (= "border-radius: 980px;"  (str/trim (css/style {:border-radius 980}))) "kebab prop")
  (is (= "background: #0071e3;"   (str/trim (css/style {:background "#0071e3"}))) "string passthrough"))

(deftest css-rules-and-keyframes
  (is (= ".btn { background: blue; border-radius: 980px; }"
         (css/rule ".btn" {:background :blue :border-radius 980})))
  (let [k (css/kf :gkdrop [[0 {:transform "translateY(-130px)" :opacity 0}]
                           [100 {:transform "translateY(0)"}]])]
    (is (str/starts-with? k "@keyframes gkdrop {"))
    (is (str/includes? k "0% { transform: translateY(-130px); opacity: 0; }"))
    (is (str/includes? k "100% { transform: translateY(0); }")))
  (let [sheet (css/css {:rules {".a" {:color :red}} :keyframes {:spin [[0 {:opacity 0}]]}})]
    (is (str/includes? sheet ".a { color: red; }"))
    (is (str/includes? sheet "@keyframes spin {"))))

(let [{:keys [fail error]} (run-tests 'dsl-test)]
  (when (pos? (+ fail error))
    (throw (ex-info "dsl tests failed" {:fail fail :error error}))))
