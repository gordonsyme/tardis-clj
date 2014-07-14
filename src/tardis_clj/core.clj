(ns tardis-clj.core
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :refer (infof errorf)]
            [me.raynes.fs :as fs]
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
  (let [get-subtree (fn [tree] (get-in tree (fs/split from)))
        update-tree (fn [tree-map]
                      (let [subtree (get-subtree (:tree tree-map))]
                        (assoc tree-map :tree subtree)))]
    (->> manifest
         :trees
         (map update-tree))))

(defn leaf?
  [node]
  (and (:key node) (:metadata node)))

(defn restore-tree
  [store tree dir]
  {:pre [(fs/directory? dir)]}
  (doseq [[pathvec file-map] (take 10 tree)]
    (let [new-path (apply io/file dir pathvec)
          restore? (or (not (fs/exists? new-path))
                       (not= (:key file-map)
                             (:key (tree/create-file-map new-path))))]
      (infof "Restoring %s? %s" pathvec restore?)
      (when restore?
        (try
          (storage/restore store new-path file-map)
          (catch Exception e
            (errorf e "Unable to restore %s to %s" pathvec new-path)))))))

(defn restore
  [manifest from-dir to-dir]
  {:pre [(fs/directory? to-dir)]}
  (let [flatten-tree (partial tree/flatten-tree leaf?)
        from-trees (restorable-trees manifest from-dir)]
    (doseq [{:keys [tree store]} from-trees]
      (restore-tree store (flatten-tree tree) to-dir))))

(defn -main
  "I don't do a whole lot ... yet."
  [root-dir & dirs]
  (let [backup-dirs (map io/file (cons root-dir dirs))
        manifest (tree/build-manifest skip backup-dirs)]
    (println (pr-str manifest))))
