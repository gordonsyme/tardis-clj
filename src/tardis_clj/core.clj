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

(defn restore-one
  [store dir pathvec file-map]
  (let [new-path (apply io/file dir pathvec)]
    (infof "Restoring %s to %s" pathvec new-path)
    (try
      (storage/restore store new-path file-map)
      true
      (catch Exception e
        (errorf e "Unable to restore %s to %s" pathvec new-path)
        false))))

(defn restore-tree
  [store tree dir]
  {:pre [(fs/directory? dir)]}
  (tree/visit-tree leaf? tree (partial restore-one store dir)))

(defn restore
  [manifest from-dir to-dir]
  {:pre [(fs/directory? to-dir)]}
  (let [from-trees (restorable-trees manifest from-dir)]
    (doseq [{:keys [tree store]} from-trees]
      (restore-tree store tree to-dir))))

(defn restore-command
  [backup-dirs]
  (let [manifest (tree/build-manifest skip backup-dirs)]
    (println "restore: ")
    (clojure.pprint/pprint manifest)))

(defn save-one
  [store pathvec file-map]
  (let [path (apply io/file pathvec)]
    (infof "Saving %s" path)
    (try
      (storage/save store path file-map)
      true
      (catch Exception e
        (errorf e "Unable to save %s to %s" pathvec store)
        false))))

(defn save-tree
  [store tree]
  (tree/visit-tree leaf? tree (partial save-one store)))

(defn save
  [manifest]
  (let [f (fn [{:keys [tree store] :as t}]
            (let [[successes failures] (save-tree store tree)]
              (assoc t :tree successes)))]
    (assoc manifest :trees (vec (map f (:trees manifest))))))

;; To save files:
;; * build the manifest
;;   * getting the paths to upload means flattening the manifest - how to correlate uploaded file to correct manifest entry?
;; * [being proper] copy file to temp space
;; * upload gzip to S3
;; * record file as success (uploaded or already present) or failure
;; * result is two manifests - successful files and failed files
;; * uploaded manifest is just the successful files


(defn save-command
  [backup-dirs]
  (let [manifest (tree/build-manifest skip backup-dirs)
        saved (save manifest)]
    (println "save: ")
    (clojure.pprint/pprint manifest)
    (println "saved: ")
    (clojure.pprint/pprint saved)
    (println "equal?: " (= manifest saved))))

(defn -main
  "I don't do a whole lot ... yet."
  [command root-dir & dirs]
  (init)
  (let [backup-dirs (map io/file (cons root-dir dirs))]
    (case command
      "restore" (restore-command backup-dirs)
      "save" (save-command backup-dirs))))
