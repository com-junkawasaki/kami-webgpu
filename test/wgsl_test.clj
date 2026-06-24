(ns wgsl-test
  "Golden tests for kami.wgsl — the shader hiccup. They pin that an EDN AST compiles to the WGSL a
   game expects: operator/precedence, function calls, vecN<f32> constructors, kebab→snake idents,
   statements, and a real lighting fragment function. The compiler being .cljc, this same source is
   what the browser (shadow-cljs) emits, so the test guards the live shader too."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [kami.wgsl :as w]))

(deftest expressions
  (is (= "(a + b)"            (w/expr [:+ :a :b])))
  (is (= "(a * b * c)"        (w/expr [:* :a :b :c])))
  (is (= "(-x)"              (w/expr [:- :x])))
  (is (= "max(dot(N, L), 0.0)" (w/expr [:max [:dot :N :L] 0.0])))
  (is (= "vec4<f32>(c, 1.0)" (w/expr [:vec4 :c 1.0])))
  (is (= "g.sun_dir.xyz"     (w/expr :g.sun-dir.xyz)) "kebab→snake, dotted path + swizzle kept")
  (is (= "pow(x, 2.0)"       (w/expr [:pow :x 2.0])))
  (is (= "mix(a, b, t)"      (w/expr [:mix :a :b :t]))))

(deftest statements
  (is (= "let ndl = max(dot(N, L), 0.0);" (w/stmt [:let :ndl [:max [:dot :N :L] 0.0]])))
  (is (= "var c: vec3 = vec3<f32>(0.0, 0.0, 0.0);" (w/stmt [:var :c :vec3 [:vec3 0.0 0.0 0.0]])))
  (is (= "return vec4<f32>(c, 1.0);" (w/stmt [:return [:vec4 :c 1.0]])))
  (is (str/starts-with? (w/stmt [:if [:> :a 0.0] [[:set :c :a]]]) "if ((a > 0.0)) {")))

(deftest a-lighting-fragment-compiles
  (let [src (w/func :fs {:stage :fragment :params [[:i :VO]] :ret [:loc 0 [:vec4 :f32]]}
                    [:let :N   [:normalize :i.n]]
                    [:let :L   [:normalize [:- :g.sun-dir.xyz]]]
                    [:let :ndl [:max [:dot :N :L] 0.0]]
                    [:return [:vec4 [:* :i.col :ndl] 1.0]])]
    (is (str/includes? src "@fragment"))
    (is (str/includes? src "fn fs(i: VO) -> @location(0) vec4<f32> {"))
    (is (str/includes? src "let ndl = max(dot(N, L), 0.0);"))
    (is (str/includes? src "return vec4<f32>((i.col * ndl), 1.0);"))
    (is (= src (apply w/func :fs {:stage :fragment :params [[:i :VO]] :ret [:loc 0 [:vec4 :f32]]}
                      [[:let :N   [:normalize :i.n]]
                       [:let :L   [:normalize [:- :g.sun-dir.xyz]]]
                       [:let :ndl [:max [:dot :N :L] 0.0]]
                       [:return [:vec4 [:* :i.col :ndl] 1.0]]]))
        "deterministic")))

(let [{:keys [fail error]} (run-tests 'wgsl-test)]
  (when (pos? (+ fail error))
    (throw (ex-info "wgsl tests failed" {:fail fail :error error}))))
