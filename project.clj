(defproject edeposit.amqp.kramerius "1.0.0"
  :description "AMQP modul for export data from eDeposit into Kramerius. 
Is is a part of a project eDeposit. The project solves contributing 
of electronic content for National Library of Czech Republic"
  :url "https://github.com/edeposit/edeposit.amqp.kramerius"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [com.novemberain/langohr "3.2.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [commons-codec/commons-codec "1.10"]
                 [clj-time "0.11.0"]
                 [me.raynes/fs "1.4.6"]
                 [org.clojure/data.json "0.2.5"]
                 [environ "1.0.0"]
                 [hiccup "1.0.5"]
                 [reloaded.repl "0.1.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [danlentz/clj-uuid "0.1.6"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/data.zip "0.1.1"]
                 [clojurewerkz/serialism "1.3.0"]
                 [com.draines/postal "1.11.3"]
                 [clj-ssh "0.5.11"]
                 ]
  :plugins [
            ;[com.cemerick/clojurescript.test "0.3.3"]
            ]

  :main edeposit.amqp.kramerius.core
  :profiles {:dev {:plugins [
                             [quickie "0.4.0"]
                             [spyscope "0.1.5"]
                             [cider/cider-nrepl "0.9.1"]
                             [refactor-nrepl "1.1.0"]
                             ]}
             :uberjar {:aot :all}
             }
)
