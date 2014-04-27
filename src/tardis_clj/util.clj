(ns tardis-clj.util)

(defmacro inspect
  [form]
  `(let [result# ~form]
    (println (format "%s is %s" (pr-str '~form) result#))
    result#))
