(ns tardis-clj.csv
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [me.raynes.fs :as fs]
            [tardis-clj.nio :as nio]
            [tardis-clj.util :refer (inspect)]))

(set! *warn-on-reflection* true)

(defn parse-csv-line
  [[path key owner group octal-mode ctime mtime size]]
  (let [->int (fn [s] (Integer/parseInt s))]
    [path
     {:key key
      :metadata {:owner owner
                 :group group
                 :mode (nio/->mode-string (->int octal-mode))
                 :ctime (->int ctime)
                 :mtime (->int mtime)
                 :size (->int size)}}]))

(defn update-tree [tree csv-line]
  (let [[path entry] (parse-csv-line csv-line)]
    (assoc-in tree (fs/split path) entry)))

(defn load-csv-manifest
  [manifest store]    ; TODO more requirement for some schema and types
  {:pre [(fs/exists? manifest)]}
  (with-open [in (io/reader manifest)]
    {:trees [
      {:tree (reduce update-tree {} (rest (csv/read-csv in :separator \:)))}]}))
