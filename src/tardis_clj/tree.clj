(ns tardis-clj.tree
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.zip :as z]
            [clojure.tools.logging :refer (infof)]
            [me.raynes.fs :as fs]
            [clj-time.core :as time]
            [tardis-clj.nio :as nio]
            [tardis-clj.crypto :as crypto]
            [tardis-clj.util :as util :refer (inspect)])
  (:import [java.io File]))

(set! *warn-on-reflection* true)

(defn shasum
  [^File file]
  (let [buffer-size (* 32 1024)
        buffer (byte-array buffer-size)]
    (with-open [stream (io/input-stream file)]
      (crypto/sha1sum (nio/chunked-read stream buffer buffer-size)))))

(defn ->key
  [^File file]
  "A key for the blob representing this file. Formed from the sha1 of the
  filename followed by a '/' and the sha1 of the file content"
  (let [name-bytes [(.getBytes (fs/base-name file))]]
    (str (crypto/sha1sum name-bytes) "/" (shasum file))))

(defn create-file-map
  [file]
  ; TODO manifest format needs to be defined as a schema
  (infof "building file-map for %s" file)
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
  [store-details skip-fn user dirs]
  (let [trees (map (partial build-tree skip-fn) dirs)]
    {:timestamp (.getMillis (time/now))
     :tree (apply util/deep-merge trees)
     :owner user
     :store store-details}))

(defn build-manifest
  "Format is:
  {:data [{:timestamp ts
           :tree { ... }
           :owner Bob
           :store { ... details ... }}]
   :version \"1.0\"}"
  [store-details skip-fn dirs]
  ;; FIXME putting the store details here is a horrible hack
  (let [user (System/getenv "USER")]
    {:data (build-manifest-tree store-details skip-fn user dirs)
     :version "1.0"}))

(defn tree-zipper*
  [leaf? tree]
  (let [inner-node? (fn [n] (and (map? n) (not (leaf? n))))
        branch? (fn [n]
                  (or (inner-node? n)
                      (inner-node? (second n))))
        children (fn [n]
                  (seq (if (inner-node? n)
                        n
                        (second n))))
        make-node (fn [n children]
                    (throw (ex-info "unsupported operation" {})))]
    (z/zipper branch? children make-node tree)))

(defn tree-zipper
  "A tree is a nested map. To ensure that all trees have a single root a
  synthetic root node is inserted for the zipper. The other zipper related fns
  correctly handle this synthetic root node.
  E.g.
  { \"/\" { \"home\" { \"alice\" {...}
                       \"bob\" {...}}}}

  Editing nodes is unsupported"
  [leaf? tree]
  (let [inner-node? (fn [n] (and (map? n) (not (leaf? n))))
        branch? (fn [n] (inner-node? (second n)))
        children (fn [n] (seq (second n)))
        make-node (fn [n children]
                    (throw (ex-info "unsupported operation" {})))]
    (z/zipper branch? children make-node ["synthetic-root" tree])))

(defn- tree-zipper-path
  "Get the path to the currently selected node in the tree, as a vector."
  [tz]
  (conj (->> tz z/path rest (map first) vec) (-> tz z/node first)))

(defn- visit-tree*
  [leaf? zipper visit-fn success failure]
  (let [node-data (-> zipper z/node second)
        path (tree-zipper-path zipper)
        ;; Ewww... However this does do the right thing :(
        [s f] (if (leaf? node-data)
                (if (visit-fn path node-data)
                  [(assoc-in success path node-data) failure]
                  [success (assoc-in failure path node-data)])
                [success failure])]
    (if-not (z/end? zipper)
      (recur leaf? (z/next zipper) visit-fn s f)
      [s f])))

(defn visit-tree
  "Visit each leaf in tree. 'visit-fn' is a two-arg function that is passed the
  path to the leaf (as a vector)
  Returns a pair of trees, the first containing all the successes, the second
  containing the failures."
  [leaf? tree visit-fn]
  (let [zipper (tree-zipper leaf? tree)]
    (when-not (z/end? zipper)
      (visit-tree* leaf? zipper visit-fn {} {}))))
