(ns tardis-clj.storage-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs]
            [tardis-clj.util :refer (with-temp-file inspect)]
            [tardis-clj.nio :as nio]
            [tardis-clj.tree :as tree]
            [tardis-clj.storage :as storage]))

(deftest needs-update-files-are-same-age
  (with-temp-file file
    (with-open [w (io/writer file)]
      (.write w "hello world"))
    (let [file-map (tree/create-file-map file)]
      (testing "files are equal"
        (is (not (storage/needs-update file file-map))))
      (testing "local file is same age and different"
        (let [file-map (-> file-map
                           (assoc :key "wurble"))]
          (is (storage/needs-update file file-map)))))))

(deftest needs-update-local-file-is-newer
  (with-temp-file file
    (with-open [w (io/writer file)]
      (.write w "hello world"))
    (let [file-map (tree/create-file-map file)]
      (testing "local file is newer"
        (let [file-map (-> file-map
                           (assoc-in [:metadata :mtime] (inc (nio/mtime file))))]
          (is (not (storage/needs-update file file-map)))))
      (testing "local file is newer and different"
        (let [file-map (-> file-map
                           (assoc-in [:metadata :mtime] (inc (nio/mtime file)))
                           (assoc :key "wurble"))]
          (is (not (storage/needs-update file file-map))))))))

(deftest needs-update-local-file-is-older
  (with-temp-file file
    (with-open [w (io/writer file)]
      (.write w "hello world"))
    (let [file-map (tree/create-file-map file)]
      (testing "local file is older"
        (let [file-map (-> file-map
                           (assoc-in [:metadata :mtime] (dec (nio/mtime file))))]
          (is (not (storage/needs-update file file-map)))))
      (testing "local file is older and different"
        (let [file-map (-> file-map
                           (assoc-in [:metadata :mtime] (dec (nio/mtime file)))
                           (assoc :key "wurble"))]
          (is (storage/needs-update file file-map)))))))
