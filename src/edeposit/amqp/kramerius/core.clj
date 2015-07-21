(ns edeposit.amqp.kramerius.core
  (:require
   [clojure.tools.cli :as cli]
   [clojure.tools.nrepl.server :refer (start-server)]
   [edeposit.amqp.kramerius.systems :refer [prod-system]]
   [reloaded.repl :refer [system init start stop go reset]]
   [rx.lang.clojure.core :as rx]
   [edeposit.amqp.kramerius.handlers :as h]
   )
  (:gen-class :main true)
)

(defn obs->obs
  "Takes input observables and transform them into output observables.
  This is main logic of kramerius.

  It returns 3 Observables
  - requests to convert marcxml2mods
  - requests to export result to ssh
  - response messages to send back a results

  3 input Obs:
  - requests to export
  - marcxml-to-mods responses
  - ssh export responses

  ## Request to export
  [metadata payload]

  ## Marcxml-to-mods response
  
  ## SSH export response
  "
  [requests-obs marcxml-to-mods-response-obs ssh-response-obs]
  (def workdir-obs (->> requests-obs
                        (rx/map h/save-request-payload)
                        ))
  )


(defn -main [& args]
    (let [ [options args banner] 
         (cli/cli args
                  [ "-f" "--file"]
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
    (when (:file options)
      (println "file")
      )
    )
  )