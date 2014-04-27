(ns tardis-clj.tree
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [me.raynes.fs :as fs]
            [tardis-clj.nio :as nio]
            [tardis-clj.crypto :as crypto]
            [tardis-clj.util :refer (inspect)])
  (:import [java.io File]))

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
  {:key (->key file)
   :metadata {:owner (nio/owner file)
              :group (nio/group file)
              :mode (nio/mode file)}})

(defn- handle-files
  [dir files]
  (let [bar (for [file files]
              [file (create-file-map (clojure.java.io/file dir file))])]
    (reduce conj {} bar)))

(defn- update-tree
  "Add data for all the files in 'root' to the manifest tree"
  [tree [root dirs files]]
  (assoc-in tree (fs/split (str root)) (handle-files root files)))

(defn build-manifest
  [dir]
  {:pre [(fs/directory? dir)]}
  (let [path (fs/normalized-path dir)]
    (reduce update-tree {} (fs/iterate-dir path))))
