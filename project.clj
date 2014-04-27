(defproject tardis-clj "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "GNU General Public License Version 3"
            :url "https://www.gnu.org/licenses/gpl-3.0.txt"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.typed "0.2.44"]
                 [me.raynes/fs "1.4.4"]
                 [amazonica "0.2.12"]]
  :main ^:skip-aot tardis-clj.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  :test-selectors {:default (complement :integration)
                   :integration :integration
                   :all (fn [_] true)})
