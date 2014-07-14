(ns tardis-clj.util
  (:require [me.raynes.fs :as fs]))

(defmacro inspect
  [form]
  `(let [result# ~form]
    (println (format "%s is %s" (pr-str '~form) result#))
    result#))


(defmacro with-temp-file
  [filename & body]
  `(let [~filename (fs/temp-file "tmp")]
    (try
      ~@body
      (finally
        (fs/delete ~filename)))))


(defmacro with-temp-dir
  [dirname & body]
  `(let [~dirname (fs/temp-dir "tmp")]
    (try
      ~@body
      (finally
        (fs/delete-dir ~dirname)))))
