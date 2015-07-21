(ns edeposit.amqp.kramerius.systems
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [edeposit.amqp.kramerius.components :refer [new-kramerius-amqp]]))

(defn prod-system []
  (component/system-map
   :kramerius-amqp (new-kramerius-amqp
                    (env :kramerius-amqp-uri)
                    (env :kramerius-amqp-exchange) 
                    (env :kramerius-amqp-qname)
                    )
   )
)
