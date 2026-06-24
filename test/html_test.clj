(ns html-test
  "Golden tests for kami.html — hiccup → HTML. They pin void elements (no close tag), boolean
   attributes (bare when true, dropped when false/nil), inline mixed text+element content, text/attr
   escaping, and the html5 doctype. xmllint --html parses the same output in `bb gate`."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [kami.html :as h]))

(deftest elements-and-attrs
  (is (= "<a href=\"/x\">link</a>" (h/html [:a {:href "/x"} "link"])) "inline text child")
  (is (= "<br>"                    (h/html [:br])) "void element, no close tag")
  (is (= "<img src=\"a.png\">"      (h/html [:img {:src "a.png"}])) "void with attrs")
  (is (= "<input checked>"         (h/html [:input {:checked true}])) "boolean attr → bare")
  (is (= "<input>"                 (h/html [:input {:checked false :id nil}])) "false/nil attrs dropped")
  (is (= "<div></div>"             (h/html [:div])) "empty non-void closes")
  (is (= "<p>Some <strong>bold</strong> text</p>"
         (h/html [:p "Some " [:strong "bold"] " text"])) "inline mixed content"))

(deftest escaping-and-doctype
  (is (= "<h1>a &amp; b &lt;c&gt;</h1>" (h/html [:h1 "a & b <c>"])) "text escaped")
  (is (= "<a title=\"x&quot;y\">t</a>" (h/html [:a {:title "x\"y"} "t"])) "attr quote escaped")
  (is (str/starts-with? (h/html5 [:html [:body "hi"]]) "<!DOCTYPE html>\n<html>") "doctype prepended"))

(deftest raw-text-elements
  ;; script/style content must NOT be HTML-escaped — browsers don't decode entities inside them.
  (is (= "<script>if (a < b && c > d) x();</script>"
         (h/html [:script "if (a < b && c > d) x();"])) "JS emitted verbatim, not escaped")
  (is (= "<style>.a > .b { color: red }</style>"
         (h/html [:style ".a > .b { color: red }"])) "CSS combinator > kept verbatim")
  (is (= "<p>a &lt; b</p>" (h/html [:p "a < b"])) "ordinary text is still escaped"))

(deftest nested-block-indents
  (is (= "<ul>\n  <li>a</li>\n  <li>b</li>\n</ul>"
         (h/html [:ul [:li "a"] [:li "b"]])) "element-only children block-indent"))

(let [{:keys [fail error]} (run-tests 'html-test)]
  (when (pos? (+ fail error))
    (throw (ex-info "html tests failed" {:fail fail :error error}))))
