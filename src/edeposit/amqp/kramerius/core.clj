(ns edeposit.amqp.kramerius.core
  (:require
   [clojure.tools.cli :as cli]
   [clojure.tools.nrepl.server :refer (start-server)]
   [edeposit.amqp.kramerius.systems :refer [prod-system]]
   [reloaded.repl :refer [system init start stop go reset]]
   [edeposit.amqp.kramerius.handlers :as h]
   [reagi.core :as r]
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
  [request-obs marcxml-to-mods-response-obs ssh-response-obs]
  (let  [ workdir-obs (->> request-obs
                           (r/map h/request-with-tmpdir)
                           (r/map h/save-request))
         requests-to-marcxml2mods-obs (->> workdir-obs
                                           (r/map h/prepare-marcxml2mods-request))
         mods-files-obs (->> marcxml-to-mods-response-obs
                                  (r/map h/save-marcxml2mods-response)
                                  (r/map h/parse-mods-files)
                                  )
         requests-to-ssh-obs (->> mods-files-obs
                                  (r/map h/mods->oai_dcs)
                                  (r/map h/make-package-with-foxml mods-files-obs) ;; zip two observables mods, oai_dcs
                                  )]
    { :requests-to-marcxml2mods-obs requests-to-marcxml2mods-obs
     :requests-to-ssh requests-to-ssh-obs})
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
