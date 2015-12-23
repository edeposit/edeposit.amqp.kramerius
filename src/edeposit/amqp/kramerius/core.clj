(ns edeposit.amqp.kramerius.core
  (:require
   [clojure.tools.cli :as cli]
   [clojure.tools.nrepl.server :refer (start-server)]
   [edeposit.amqp.kramerius.systems :refer [prod-system]]
   [reloaded.repl :refer [system init start stop go reset]]
   [edeposit.amqp.kramerius.handlers :as h]
   )
  (:gen-class :main true)
)

(defn -main [& args]
    (let [ [options args banner] 
         (cli/cli args
                  [ "--amqp" :default false :flag true]
                  [ "-h" "--help" :default false :flag true]
                  )
         ]
    (when (:help options)
      (println banner)
      (System/exit 0)
      )
    (when (:amqp options)
      (defonce server (start-server :port 12345))
      (reloaded.repl/set-init! prod-system)
      (go)
      )
    )
  )
