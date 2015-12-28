(ns edeposit.amqp.kramerius.core
  (:require
   [clojure.tools.cli :as cli]
   [edeposit.amqp.kramerius.systems :refer [prod-system]]
   [edeposit.amqp.kramerius.handlers :as h]
   [com.stuartsierra.component :as component]
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
      (component/start (prod-system))
      )
    )
  )
