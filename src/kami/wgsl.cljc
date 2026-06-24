(ns kami.wgsl
  "WGSL as data — 'hiccup for shaders'. An EDN AST compiles to a WGSL source string, so a game's
   lighting / material / post-fx is authored and forked like the rest of the scene, and ONE shader
   source feeds both the web (kami.webgpu) and native (kami-webgpu-rs) executors — parity by source,
   not a hand-mirrored copy. `.cljc`: the same compiler runs on shadow-cljs (browser) and bb/JVM
   (the native fixture).

   Expressions (vectors are calls/operators; keywords/symbols are identifiers; numbers are f32):
     [:* a b]            → (a * b)        ;; +,-,*,/,<,>,<=,>=,==,&&,|| variadic; [:- a] is unary
     [:dot a b]          → dot(a, b)      ;; any other head is a function call (kebab→snake)
     [:vec4 r g b 1.0]   → vec4<f32>(r, g, b, 1.0)   ;; vec2/3/4, mat3/4 constructors
     :i.n  :g.sun-dir.xyz → i.n   g.sun_dir.xyz       ;; field paths + swizzles; kebab→snake
   Statements:
     [:let :ndl [:max [:dot :N :L] 0.0]] → let ndl = max(dot(N, L), 0.0);
     [:var :c :vec3 expr] [:set :c expr] [:return expr] [:if cond [then…] [else…]]
   Functions:
     (func :fs {:stage :fragment :params [[:i :VO]] :ret [:loc 0 [:vec4 :f32]]} stmt…)"
  (:require [clojure.string :as str]))

(defn- ident [s] (str/replace (name s) "-" "_"))

(defn- num [n]
  ;; WGSL f32 literals need a decimal point; CLJS can't tell 0 from 0.0, so float everything that
  ;; looks integral (loop indices etc. should be passed as raw strings, e.g. "0").
  (let [s (str n)]
    (if (or (str/includes? s ".") (str/includes? s "e")) s (str s ".0"))))

(def ^:private binops
  {:+ "+" :- "-" :* "*" :/ "/" :% "%"
   :< "<" :> ">" :<= "<=" :>= ">=" :== "==" :!= "!=" :&& "&&" :|| "||"})

(def ^:private ctors {:vec2 "vec2<f32>" :vec3 "vec3<f32>" :vec4 "vec4<f32>"
                      :mat3 "mat3x3<f32>" :mat4 "mat4x4<f32>"})

(declare expr)
(defn- arglist [xs] (str/join ", " (map expr xs)))

(defn expr
  "Compile an EDN expression to a WGSL expression string."
  [e]
  (cond
    (number? e)  (num e)
    (string? e)  e                         ;; raw WGSL passthrough (escape hatch)
    (or (keyword? e) (symbol? e)) (ident e) ;; var / field path / swizzle
    (vector? e)
    (let [[op & xs] e]
      (cond
        (= 1 (count (cons op xs)))  (ident op)
        (binops op) (if (= 1 (count xs))
                      (str "(" (binops op) (expr (first xs)) ")")           ;; unary (e.g. -x)
                      (str "(" (str/join (str " " (binops op) " ") (map expr xs)) ")"))
        (ctors op)  (str (ctors op) "(" (arglist xs) ")")
        :else       (str (ident op) "(" (arglist xs) ")")))                 ;; function call
    :else (str e)))

(declare stmt)
(defn- block [stmts] (str/join "\n" (map #(str "  " (str/replace (stmt %) "\n" "\n  ")) stmts)))

(defn stmt
  "Compile an EDN statement to a WGSL statement string."
  [s]
  (let [[op & xs] s]
    (case op
      :let    (str "let " (ident (first xs)) " = " (expr (second xs)) ";")
      :var    (if (= 3 (count xs))                       ;; [:var name type expr] — annotated
                (str "var " (ident (first xs)) ": "
                     (let [t (second xs)] (or (ctors t) (ident t))) " = " (expr (nth xs 2)) ";")
                (str "var " (ident (first xs)) " = " (expr (second xs)) ";"))   ;; [:var name expr] — inferred
      :set    (str (ident (first xs)) " = " (expr (second xs)) ";")
      :return (str "return " (expr (first xs)) ";")
      :if     (str "if (" (expr (first xs)) ") {\n" (block (second xs)) "\n}"
                   (when (> (count xs) 2) (str " else {\n" (block (nth xs 2)) "\n}")))
      (str (expr s) ";"))))   ;; a bare expression statement

(defn func
  "Compile a function form to a WGSL function declaration.
   opts: {:stage :vertex|:fragment? :params [[name type] …] :ret type-or-[:loc n type]}."
  [name {:keys [stage params ret]} & body]
  (let [ret* (cond
               (nil? ret) nil
               (and (vector? ret) (= :loc (first ret)))
               (str "@location(" (second ret) ") "   ;; binding index is an integer, not an f32
                    (let [t (nth ret 2)] (if (vector? t) (or (ctors (first t)) (ident (first t))) (ident t))))
               (vector? ret) (or (ctors (first ret)) (ident (first ret)))
               :else (ident ret))]
    (str (when stage (str "@" (ident stage) "\n"))
         "fn " (ident name) "("
         (str/join ", " (map (fn [[n t]] (str (ident n) ": " (if (vector? t) (or (ctors (first t)) (ident (first t))) (ident t)))) params))
         ")" (when ret* (str " -> " ret*)) " {\n"
         (block body) "\n}")))
