(ns tardis-clj.storage
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.tools.logging :refer (debugf)]
            [me.raynes.fs :as fs]
            [amazonica.core :refer (defcredential)]
            [amazonica.aws.s3 :as s3]
            [tardis-clj.nio :as nio])
  (:import [java.io File]))

(defn exists?
  [bucket-name key]
  (let [objects (:object-summaries (s3/list-objects bucket-name key))]
    (some #(= key %) (map :key objects))))

;; Should have a protocol to get/put/list objects to/from/in each different store type
;; Stores should be records
;; Except that might serialise oddly? Stick with multi-methods for now
;; (defrecord S3Store ...)
;:
;; (defprotocol Store
;;  (put-file [this key in-stream])
;;  (get-file [this key out-stream])
;;  (exists? [this key])
;;  (list [this]))


(defn ->s3-key
  [store file-map]
  (str (:key-prefix store) "/" (:key file-map)))


; (t/ann restore (Store File tree/FileMap -> Any))
(defmulti restore
  (fn [store to-file file-map] (:type store)))

;; TODO This should restore mtime and ctime too
(defmethod restore :s3
  [store ^File to-file file-map]
  (let [object (s3/get-object (:bucket store)
                              (->s3-key store file-map))]
    (debugf "restoring to %s" to-file)
    (fs/mkdirs (fs/parent to-file))
    (with-open [stream (java.util.zip.GZIPInputStream. (:object-content object))
                output (io/output-stream to-file)]
      (io/copy stream output))
    ;; Setting owner/group may not work, no guarantee that the same owner/group exist on this system
    ;(nio/set-owner to-file (-> file-map :metadata :owner))
    ;(nio/set-group to-file (-> file-map :metadata :group))
    (nio/set-mtime to-file (-> file-map :metadata :mtime))))


; (t/ann save (Store File tree/FileMap -> Any))
(defmulti save
  (fn [store from-file file-map] (:type store)))

(defmethod save :s3
  [store ^File from-file file-map]
  (debugf "saving %s to %s" from-file file-map)
  (with-open [stream (io/output-stream from-file)
              output (java.util.zip.GZIPOutputStream. stream)]
    (s3/put-object :bucket-name (:bucket store)
                   :key (->s3-key store file-map)
                   :input-stream output
                   :metadata {})))


(defn init
  [credentials-path]
  (let [credentials (edn/read-string (slurp credentials-path))]
    (defcredential (:access-key credentials)
                   (:secret-key credentials)
                   (:endpoint credentials))))
