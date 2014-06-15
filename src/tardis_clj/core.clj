(ns tardis-clj.core
  (:require [clojure.java.io :as io]
            [tardis-clj.storage :as storage]
            [tardis-clj.tree :as tree]
            [tardis-clj.util :refer (inspect)])
  (:import [java.io File])
  (:gen-class))

(set! *warn-on-reflection* true)

(defn skip
  [^File file]
  (re-find #"\/\.git(?:$|/)" (.getPath file)))

(defn init
  []
  (storage/init (io/file (System/getenv "HOME") ".tardis")))

(defn restorable-trees
  [manifest from]
  (:trees manifest)
  (let [get-subtree (fn [tree] (get-in tree (fs/split from)))]
    (->> manifest
         :trees
         (map :tree)
         (map get-subtree))))

;;(defn restore-tree
;;  [tree dir]
;;  {:pre [(fs/directory? dir)]}
;;  (doseq [[path {:keys [key]}] tree]

(defn leaf?
  [node]
  (and (:key node) (:metadata node)))

(defn restore
  [manifest from to]
  (let [trees (map (partial tree/flatten-tree leaf?) (restorable-trees manifest from))]
    trees))

(defn -main
  "I don't do a whole lot ... yet."
  [root-dir & dirs]
  (let [backup-dirs (map io/file (inspect (cons root-dir dirs)))
        manifest (tree/build-manifest skip backup-dirs)]
    (println (pr-str manifest))))
