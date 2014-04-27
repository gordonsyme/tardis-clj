(defproject tardis-clj "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "GNU General Public License Version 3"
            :url "https://www.gnu.org/licenses/gpl-3.0.txt"}
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :main ^:skip-aot tardis-clj.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
