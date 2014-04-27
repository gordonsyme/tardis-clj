(ns tardis-clj.core
  (:require [tardis-clj.tree :as tree])
  (:gen-class))

(defn -main
  "I don't do a whole lot ... yet."
  [root-dir & args]
  (println (pr-str (tree/build-manifest root-dir))))
