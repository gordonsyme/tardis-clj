(ns tardis-clj.nio
  (:require [clojure.core.typed :as t]
            [tardis-clj.util :refer (inspect)])
  (:import [java.io File
                    Reader]
           [java.nio.file Files
                          LinkOption
                          attribute.FileTime
                          attribute.PosixFileAttributeView
                          attribute.PosixFilePermission
                          attribute.PosixFilePermissions]
           [java.util.concurrent TimeUnit]))

(set! *warn-on-reflection* true)

(t/def-alias ModeString String)


(def default-link-options
  (into-array java.nio.file.LinkOption []))


(t/ann attribute-view [File -> PosixFileAttributeView])
(defn attribute-view
  [^File file]
  (Files/getFileAttributeView (.toPath file)
                              PosixFileAttributeView
                              default-link-options))

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

(defn- integral->mode-string
  [octal-mode]
  (let [lookup (fn [n] (get octal-modestring-lookup (bit-and n 0x7)))
        foo (fn [n] (take 3 (iterate (fn [i] (bit-shift-right i 3)) n)))]
    (->> (foo octal-mode)
         (map lookup)
         reverse
         (apply str))))

(extend-protocol FileMode
  Long
  (->mode-string [octal-mode]
    (integral->mode-string octal-mode))
;;  (->permissions [octal-mode]
;;    #{PosixFilePermission/OWNER_READ PosixFilePermission/OWNER_WRITE PosixFilePermission/OWNER_EXECUTE
;;      PosixFilePermission/OTHERS_READ PosixFilePermission/OTHERS_WRITE PosixFilePermission/OTHERS_EXECUTE})
;;  (->octal-mode [octal-mode]
;;    octal-mode)
  )

;; TODO There is a way to use the protocol info for Long for Integer too
(extend-protocol FileMode
  Integer
  (->mode-string [octal-mode]
    (integral->mode-string octal-mode)))

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
  "Not actually proper ctime, NIO doesn't provide access to the ctime and
  instead gives you 'creationTime', which is mtime on a POSIX file system. Go
  figure."
  [^File file]
  (-> file
      attribute-view
      .readAttributes
      .creationTime
      .toMillis
      (/ 1000)))

(t/ann mtime [File -> Long])
(defn mtime
  [^File file]
  (-> file
      attribute-view
      .readAttributes
      .lastModifiedTime
      .toMillis
      (/ 1000)))

(t/ann set-mtime [File Long -> Any])
(defn set-mtime
  [^File file timestamp]
  (Files/setLastModifiedTime (.toPath file) (FileTime/from timestamp TimeUnit/SECONDS)))

(t/ann size [File -> Long])
(defn size
  [^File file]
  (Files/size (.toPath file)))


; (t/ann chunked-read [InputStream ByteArray Int -> Seq(ByteArray)])
(defn chunked-read
  [stream buffer buffer-size]
  (let [n (.read stream buffer 0 buffer-size)]
    (cond
      (< n 0) '()
      (== 0 n) (recur stream buffer buffer-size)
      :else (cons (byte-array (take n buffer))
                  (lazy-seq (chunked-read stream buffer buffer-size))))))
