(defproject edeposit.amqp.kramerius "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [io.reactivex/rxclojure "1.0.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [com.novemberain/langohr "3.2.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [commons-codec/commons-codec "1.10"]
                 [clj-time "0.8.0"]
                 [me.raynes/fs "1.4.6"]
                 [org.clojure/data.json "0.2.5"]
                 [environ "1.0.0"]
                 [reloaded.repl "0.1.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [danlentz/clj-uuid "0.1.6"]
                 ]
  :main edeposit.amqp.kramerius.core
  :profiles {:dev {:plugins [
                             [quickie "0.4.0"]
                             ]}
             :uberjar {:aot :all}
             }
)