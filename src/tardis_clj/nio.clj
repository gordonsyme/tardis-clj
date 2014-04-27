(ns tardis-clj.nio
  (:require [clojure.core.typed :as t]
            [tardis-clj.util :refer (inspect)])
  (:import [java.io File
                    Reader]
           [java.nio.file Files
                          LinkOption
                          attribute.PosixFileAttributeView
                          attribute.PosixFilePermissions]))

(set! *warn-on-reflection* true)

(t/def-alias ModeString String)

(t/ann attribute-view [File -> PosixFileAttributeView])
(defn attribute-view
  [^File file]
  (Files/getFileAttributeView (.toPath file)
                              PosixFileAttributeView
                              (into-array java.nio.file.LinkOption [])))

(t/ann mode [File -> ModeString])
(defn mode
  [^File file]
  (-> file
      attribute-view
      .readAttributes
      .permissions
      PosixFilePermissions/toString))

(t/ann owner [File -> String])
(defn owner
  [^File file]
  (-> file
      attribute-view
      .readAttributes
      .owner
      str))

(t/ann group [File -> String])
(defn group
  [^File file]
  (-> file
      attribute-view
      .readAttributes
      .group
      str))

(defn chunked-read
  [stream buffer buffer-size]
  (let [n (.read stream buffer 0 buffer-size)]
    (cond
      (< n 0) '()
      (== 0 n) (recur stream buffer buffer-size)
      :else (cons (byte-array (take n buffer))
                  (lazy-seq (chunked-read stream buffer buffer-size))))))
