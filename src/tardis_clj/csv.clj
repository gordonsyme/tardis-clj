(ns tardis-clj.csv
  (:require [clojure.string :refer (replace-first)]
            [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [me.raynes.fs :as fs]
            [tardis-clj.nio :as nio]
            [tardis-clj.util :refer (inspect)]))

(set! *warn-on-reflection* true)

(defn parse-csv-line
  [store [path key owner group octal-mode ctime mtime size]]
  (let [->int (fn [s] (Integer/parseInt s))
        prefix (re-pattern (str "^" (:key-prefix store) "/"))]
    [path
     {:key (replace-first key prefix "")
      :metadata {:owner owner
                 :group group
                 :mode (nio/->mode-string (->int octal-mode))
                 :ctime (->int ctime)
                 :mtime (->int mtime)
                 :size (->int size)}}]))

(defn update-tree [store tree csv-line]
  (let [[path entry] (parse-csv-line store csv-line)]
    (assoc-in tree (fs/split path) entry)))

(defn load-csv-manifest
  [manifest store]    ; TODO more requirement for some schema and types
  {:pre [(fs/exists? manifest)]}
  (let [update-fn (partial update-tree store)]
    (with-open [in (io/reader manifest)]
      {:trees
        [{:tree (reduce update-fn {} (rest (csv/read-csv in :separator \:)))
          :store store}]})))
