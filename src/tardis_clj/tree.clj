(ns tardis-clj.tree
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [me.raynes.fs :as fs]
            [clj-time.core :as time]
            [tardis-clj.nio :as nio]
            [tardis-clj.crypto :as crypto]
            [tardis-clj.util :refer (inspect)])
  (:import [java.io File]))

(set! *warn-on-reflection* true)

(defn shasum
  [^File file]
  (let [buffer-size (* 32 1024)
        buffer (byte-array buffer-size)]
    (with-open [stream (io/input-stream file)]
      (crypto/sha1sum (nio/chunked-read stream buffer buffer-size)))))

(defn- ->key
  [^File file]
  "A key for the blob representing this file. Formed from the sha1 of the
  filename followed by a '/' and the sha1 of the file content"
  (let [name-bytes [(.getBytes (fs/base-name file))]]
    (str (crypto/sha1sum name-bytes) "/" (shasum file))))

(defn- create-file-map
  [file]
  ; TODO manifest format needs to be defined as a schema
  {:key (->key file)
   :metadata {:owner (nio/owner file)
              :group (nio/group file)
              :mode (nio/mode file)
              :ctime (nio/ctime file)
              :mtime (nio/mtime file)
              :size (nio/size file)}})

(defn- handle-files
  [dir files]
  (let [bar (for [file files]
              [file (create-file-map (clojure.java.io/file dir file))])]
    (reduce conj {} bar)))

(defn- update-tree
  "Add data for all the files in 'root' to the manifest tree"
  [tree [root dirs files]]
  (assoc-in tree (fs/split root) (handle-files root files)))

(defn- build-tree
  [skip-fn dir]
  {:pre [(fs/directory? dir)]}
  (let [path (fs/normalized-path dir)
        foo (filter (complement #(skip-fn (first %))) (fs/iterate-dir path))]
    (reduce update-tree {} foo)))

(defn- build-manifest-tree
  [store-details skip-fn user dir]
  {:timestamp (.getMillis (time/now))
   :tree (build-tree skip-fn dir)
   :owner user
   :store store-details})

(defn build-manifest
  [skip-fn dirs]
  (let [store-details {:bucket "net.twiceasgood.backup" :key-prefix "data"}
        user (System/getenv "USER")
        trees (for [dir dirs]
          (build-manifest-tree store-details skip-fn user dir))]
    {:trees (vec trees)}))


(defn- flatten-tree*
  [leaf? ks tree]
  (if (leaf? tree)
    {ks tree}
    (apply merge
      (for [k (keys tree)]
        (flatten-tree* leaf? (conj ks k) (get tree k))))))

(defn flatten-tree
  [leaf? tree]
  (flatten-tree* leaf? [] tree))
