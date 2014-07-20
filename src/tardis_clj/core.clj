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
         :data
         update-tree)))

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
  (let [{:keys [tree store] :as t} (restorable-trees manifest from-dir)
        [successes failures] (restore-tree store tree to-dir)
        new-manifest (assoc manifest :data (assoc t :tree successes))]
    (with-open [w (io/writer "restore-failures.edn")]
      (.write w (pr-str failures)))
    new-manifest))

(defn restore-command
  [store from-dir to-dir]
  (let [manifest (storage/get-manifest store)]
    (println "restore manifest: ")
    (clojure.pprint/pprint manifest)
    (infof "Restoring %s to %s" from-dir to-dir)
    (restore manifest from-dir to-dir)))

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
  (let [{:keys [tree store] :as t} (:data manifest)
        [successes failures] (save-tree store tree)
        new-manifest (assoc manifest :data (assoc t :tree successes))]
    (storage/update-manifest store new-manifest)
    (with-open [w (io/writer "save-failures.edn")]
      (.write w (pr-str failures)))
    new-manifest))


;; To save files:
;; * build the manifest
;;   * getting the paths to upload means flattening the manifest - how to correlate uploaded file to correct manifest entry?
;; * [being proper] copy file to temp space
;; * upload gzip to S3
;; * record file as success (uploaded or already present) or failure
;; * result is two manifests - successful files and failed files
;; * uploaded manifest is just the successful files


(defn save-command
  [store backup-dirs]
  (let [manifest (tree/build-manifest store skip backup-dirs)
        saved (save manifest)]
    (println "save: ")
    (clojure.pprint/pprint manifest)
    (println "saved: ")
    (clojure.pprint/pprint saved)
    (println "equal?: " (= manifest saved))))

(defn -main
  "I don't do a whole lot ... yet."
  [command & args]
  (init)
  (let [store {:type :s3 :bucket "net.twiceasgood.backup" :key-prefix "data"}]
    (case command
      "restore"
        (let [[from-dir to-dir & _] args]
          (restore-command store (io/file from-dir) (io/file to-dir)))
      "save"
        (let [[root-dir & other-dirs] args]
          (save-command store (map io/file (cons root-dir other-dirs)))))))
