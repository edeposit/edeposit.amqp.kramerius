(ns edeposit.amqp.kramerius.systems
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [edeposit.amqp.kramerius.components :refer [amqp-middleware
                                                        rabbit-mq]]))

(defn prod-system []
  (component/system-map
   :kramerius-amqp (rabbit-mq (env :kramerius-amqp-uri))
   :marcxml2mods-amqp (rabbit-mq (env :marcxml2mods-amqp-uri))
   :storage-amqp (rabbit-mq (env :storage-amqp-uri))

   :kramerius-middleware-marcxml2mods (->
                                       (amqp-middleware 
                                        )
                                       (component/using :kramerius-amqp)
                          )
   )
)
