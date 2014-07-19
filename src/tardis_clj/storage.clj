(ns tardis-clj.storage
  (:require [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.tools.logging :refer (debugf infof)]
            [me.raynes.fs :as fs]
            [amazonica.core :refer (defcredential)]
            [amazonica.aws.s3 :as s3]
            [tardis-clj.nio :as nio]
            [tardis-clj.util :refer (with-temp-file inspect)]
            [tardis-clj.tree :as tree])
  (:import [java.io File
                    PushbackReader]))

(set! *warn-on-reflection* true)

(defn exists?
  [bucket-name key]
  (let [objects (:object-summaries (s3/list-objects :bucket-name bucket-name
                                                    :prefix key))]
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


(defn needs-update
  [^File file file-map]
  ;; FIXME I don't like that this needs access to the tree ns
  (let [files-equal? (delay (= (tree/->key file) (:key file-map)))
        local-newer? (delay (> (-> file-map :metadata :mtime)
                               (nio/mtime file)))]
    (not
      (and (fs/exists? file)
           (or @files-equal?
               @local-newer?)))))

(defn ->s3-key
  [store file-map]
  (str (:key-prefix store) "/" (:key file-map)))


; (t/ann restore [Store File tree/FileMap -> Any])
(defmulti restore
  (fn [store to-file file-map] (:type store)))

;; TODO This should restore mtime and ctime too
(defmethod restore :s3
  [store ^File to-file file-map]
  (infof "fetching %s %s" (:bucket store) (->s3-key store file-map))
  (let [object (s3/get-object :bucket-name (:bucket store)
                              :key (->s3-key store file-map))]
    (when (needs-update to-file (:key file-map))
      (debugf "restoring to %s" to-file)
      (fs/mkdirs (fs/parent to-file))
      (with-open [stream (java.util.zip.GZIPInputStream. (:object-content object))
                  output (io/output-stream to-file)]
        (io/copy stream output))
      ;; Setting owner/group may not work, no guarantee that the same owner/group exist on this system
      ;(nio/set-owner to-file (-> file-map :metadata :owner))
      ;(nio/set-group to-file (-> file-map :metadata :group))
      (nio/set-mtime to-file (-> file-map :metadata :mtime)))))


; (t/ann save [Store File tree/FileMap -> Any])
(defmulti save
  (fn [store from-file file-map] (:type store)))


(defn save-s3
  [store ^File from-file file-map]
  (with-temp-file archive
    (with-open [in-stream (io/input-stream from-file)
                stream (io/output-stream archive)
                output (java.util.zip.GZIPOutputStream. stream)]
      (io/copy in-stream output))
    (with-open [stream (io/input-stream archive)]
      (s3/put-object :bucket-name (:bucket store)
                     :key (->s3-key store file-map)
                     :input-stream stream
                     :metadata {:server-side-encryption "AES256"}))))

(defmethod save :s3
  [store ^File from-file file-map]
  (infof "saving %s to %s" from-file file-map)
  (infof "checking if object already exists...")
  (let [objects (s3/list-objects :bucket-name (:bucket store)
                                 :prefix (->s3-key store file-map))]
    (if (seq (:object-summaries (inspect objects)))
      (infof "object already stored in S3")
      (do
        (infof "saving object to S3...")
        (save-s3 store from-file file-map)))))

(defmulti get-manifest
  (fn [store] (:type store)))

(defmethod get-manifest :s3
  [store]
  (let [object (s3/get-object :bucket-name (:bucket store)
                              :key "manifests/gordon")]
    (with-open [stream (:object-content object)
                reader (PushbackReader. (io/reader stream))]
      (edn/read reader))))

(defmulti update-manifest
  (fn [store manifest] (:type store)))

(defmethod update-manifest :s3
  [store manifest]
  (with-open [manifest-stream (java.io.ByteArrayInputStream. (.getBytes (pr-str manifest)))]
    (s3/put-object :bucket-name (:bucket store)
                   :key (format "manifests/%s" (-> manifest :data :owner))
                   :input-stream manifest-stream
                   :metadata {:server-side-encryption "AES256"})))

; (t/ann init [String -> Any])
(defn init
  [credentials-path]
  (let [credentials (edn/read-string (slurp credentials-path))]
    (defcredential (:access-key credentials)
                   (:secret-key credentials)
                   (:endpoint credentials))))
