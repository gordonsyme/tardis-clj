(ns tardis-clj.util-test
  (:require [clojure.test :refer :all]
            [me.raynes.fs :as fs]
            [tardis-clj.util :as util]))

(deftest with-temp-file-works
  (util/with-temp-file foo
    (is (fs/exists? foo))))
