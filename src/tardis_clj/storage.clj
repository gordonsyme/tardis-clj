(ns tardis-clj.storage
  (:require [clojure.edn :as edn]
            [amazonica.core :refer (defcredential)]
            [amazonica.aws.s3 :as s3]))

(defn exists?
  [bucket-name key]
  (let [objects (:object-summaries (s3/list-objects bucket-name key))]
    (some #(= key %) (map :key objects))))

(def put-object (s3/put-object))

(def get-object (s3/get-object))

(defn init
  [credentials-path]
  (let [credentials (edn/read-string (slurp credentials-path))]
    (defcredential (:access-key credentials)
                   (:secret-key credentials)
                   (:endpoint credentials))))
