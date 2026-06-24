(ns sql-test
  "Golden tests for kami.sql — the SQL DDL hiccup. They pin column types (incl. sized [:varchar 255]),
   constraints (primary-key/not-null/unique/default/references), kebab→snake identifiers, CREATE TABLE/
   INDEX, INSERT with quote-escaped string literals, and DROP. sqlite3 executes the same output in
   `bb gate` (real validation)."
  (:require [clojure.test :refer [deftest is run-tests]]
            [clojure.string :as str]
            [kami.sql :as q]))

(deftest statements
  (is (= "DROP TABLE users;" (q/stmt [:drop-table :users])))
  (is (= "CREATE INDEX ix ON users (email);" (q/stmt [:create-index :ix :users [:email]])))
  (is (= "INSERT INTO users (id, name) VALUES (1, 'O''Brien');"
         (q/stmt [:insert :users [:id :name] [1 "O'Brien"]])) "quote in literal doubled")
  (is (= "CREATE TABLE t (\n  id INTEGER PRIMARY KEY,\n  org_id INTEGER REFERENCES orgs(id)\n);"
         (q/stmt [:create-table :t
                  [:col :id :integer {:primary-key true}]
                  [:col :org-id :integer {:references [:orgs :id]}]]))
      "kebab→snake idents, FK reference"))

(deftest column-types-and-constraints
  (is (= "name VARCHAR(255) NOT NULL" (#'q/column [:col :name [:varchar 255] {:not-null true}])) "sized type")
  (is (= "email TEXT UNIQUE"          (#'q/column [:col :email :text {:unique true}])))
  (is (= "created TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
         (#'q/column [:col :created :timestamp {:default :current-timestamp}])) "bareword default")
  (is (= "n INTEGER DEFAULT 0"        (#'q/column [:col :n :integer {:default 0}])) "literal default"))

(let [{:keys [fail error]} (run-tests 'sql-test)]
  (when (pos? (+ fail error))
    (throw (ex-info "sql tests failed" {:fail fail :error error}))))
