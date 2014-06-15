(ns tardis-clj.nio
  (:require [clojure.core.typed :as t]
            [tardis-clj.util :refer (inspect)])
  (:import [java.io File
                    Reader]
           [java.nio.file Files
                          LinkOption
                          attribute.PosixFileAttributeView
                          attribute.PosixFilePermission
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

(defprotocol FileMode
  (->mode-string [this] "Convert to a mode string")
  ;;(->permissions [this] "Convert to a set of NIO PosixFilePermission")
  ;;(->octal-mode [this] "Convert to an octal mode")
  )

(defonce octal-modestring-lookup
  ["---" "--x"
   "-w-" "-wx"
   "r--" "r-x"
   "rw-" "rwx"])

(extend-protocol FileMode
  Long
  (->mode-string [octal-mode]
    (let [lookup (fn [n] (get octal-modestring-lookup (bit-and n 0x7)))
          foo (fn [n] (take 3 (iterate (fn [i] (bit-shift-right i 3)) n)))]
      (->> (foo octal-mode)
           (map lookup)
           reverse
           (apply str))))
;;  (->permissions [octal-mode]
;;    #{PosixFilePermission/OWNER_READ PosixFilePermission/OWNER_WRITE PosixFilePermission/OWNER_EXECUTE
;;      PosixFilePermission/OTHERS_READ PosixFilePermission/OTHERS_WRITE PosixFilePermission/OTHERS_EXECUTE})
;;  (->octal-mode [octal-mode]
;;    octal-mode)
  )

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

(t/ann ctime [File -> Long])
(defn ctime
  [^File file]
  12345)

(t/ann mtime [File -> Long])
(defn mtime
  [^File file]
  12345)

(t/ann size [File -> Long])
(defn size
  [^File file]
  12345)


; (t/ann chunked-read [InputStream ByteArray Int -> Seq(ByteArray)])
(defn chunked-read
  [stream buffer buffer-size]
  (let [n (.read stream buffer 0 buffer-size)]
    (cond
      (< n 0) '()
      (== 0 n) (recur stream buffer buffer-size)
      :else (cons (byte-array (take n buffer))
                  (lazy-seq (chunked-read stream buffer buffer-size))))))
