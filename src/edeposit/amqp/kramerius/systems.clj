(ns edeposit.amqp.kramerius.systems
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [edeposit.amqp.kramerius.components :as c]
            [edeposit.amqp.kramerius.handlers :as h]
            [postal.core :as pc]
            [edeposit.amqp.kramerius.components :refer [amqp-middleware
                                                        rabbit-mq]]))

(defn prod-system []
  (component/system-map
   :kramerius (rabbit-mq (env :kramerius-amqp-uri))
   :marcxml2mods (rabbit-mq (env :marcxml2mods-amqp-uri))
   :storage (rabbit-mq (env :storage-amqp-uri))
   :middleware-marcxml2mods (-> (amqp-middleware
                                 {:exchanges [:export]
                                  :queues [
                                           [:kramerius
                                            :routing-keys [[:export :response]]
                                            :handler (-> h/save-marcxml2mods-response
                                                         c/raw-pass
                                                         c/to-clojure
                                                         (c/send-result-to :kramerius :internal
                                                                           :with-key :marcxml2mods.response)
                                                         c/ack)
                                            ]
                                           ]
                                  }
                                 )
                                (component/using {:rabbit :marcxml2mods
                                                  :kramerius :kramerius})
                                )
   :middleware-storage (-> (amqp-middleware
                            {:exchanges [:export]
                             :queues [
                                      [:kramerius
                                       :routing-keys [[:export :response]]
                                       :handler (-> h/save-response-from-export-to-storage
                                                    c/raw-pass
                                                    c/to-clojure
                                                    (c/send-result-to :kramerius :internal
                                                                      :with-key :storage.response)
                                                    c/ack)
                                       ]
                                      ]
                             }                            
                            )
                           (component/using {:rabbit :storage
                                             :kramerius :kramerius})
                           )
   :middleware-kramerius (-> (amqp-middleware
                              {:exchanges [:export-to-kramerius :internal]
                                :queues [[:request-saver
                                          :routing-keys [[:export-to-kramerius :request]]
                                          :handler (-> (comp h/save-request
                                                             (h/request-with-tmpdir (env :vardir)))
                                                       c/raw-pass
                                                       c/to-clojure
                                                       (c/send-result-to :kramerius :internal
                                                                         :with-key :request-save.response)
                                                       c/ack)
                                          ]
                                         [:preview-page-maker
                                          :routing-keys [[:internal :request-save.response]]
                                          :handler (-> h/make-preview-page
                                                       c/from-clojure
                                                       c/to-clojure
                                                       (c/send-result-to :kramerius :internal
                                                                         :with-key :make-preview.response)
                                                       c/ack)
                                          ]
                                         [:marcxml2mods
                                          :routing-keys [[:internal :request-save.response]]
                                          :handler (-> h/prepare-marcxml2mods-request
                                                       c/from-clojure
                                                       c/to-json
                                                       (c/send-result-to :marcxml2mods
                                                                         :export
                                                                         :with-key :request)
                                                       c/ack)
                                          ]
                                         [:mods
                                          :routing-keys [[:internal :marcxml2mods.response]]
                                          :handler (-> (comp h/add-urnnbn-to-mods
                                                             h/parse-mods-files)
                                                       c/from-clojure
                                                       c/to-clojure
                                                       (c/send-result-to :kramerius :internal
                                                                         :with-key :mods.created)
                                                       c/ack)
                                          ]
                                         [:foxml
                                          :routing-keys [[:internal :mods.created]]
                                          :handler (-> (comp h/make-zip-package
                                                             (partial apply h/make-package-with-foxml)
                                                             h/with-oai_dcs)
                                                       c/from-clojure
                                                       c/to-clojure
                                                       (c/send-result-to :kramerius :internal
                                                                         :with-key :foxml-package.created)
                                                       c/ack)
                                          ]
                                         [:storage
                                          :routing-keys [[:internal :foxml-package.created]]
                                          :handler  (-> h/prepare-request-for-export-to-storage
                                                        c/from-clojure
                                                        c/to-json
                                                        (c/send-result-to :storage :export
                                                                          :with-key :request)
                                                        c/ack)
                                          
                                          
                                          ]
                                         [:email
                                          :routing-keys [[:internal :storage.response]]
                                          :handler (->
                                                    (comp
                                                     (h/sendmail pc/send-message)
                                                     ;; (h/sendmail (fn [msg]
                                                     ;;               {:code 0,
                                                     ;;                :error :SUCCESS,
                                                     ;;                :message "message sent"}))
                                                     h/save-email-at-workdir
                                                     (h/make-email :from (env :email-to-kramerius-from)
                                                                   :to (env :email-to-kramerius-from))
                                                     (fn [x] (h/prepare-email
                                                             x
                                                             :import-mount (env :import-mount)
                                                             :archive-mount (env :archive-mount)
                                                             :originals-mount (env :originals-mount)
                                                             )
                                                       )
                                                     )
                                                    c/from-clojure
                                                    c/to-clojure
                                                    (c/send-result-to :kramerius :internal
                                                                      :with-key :email-to-kramerius.sent)
                                                    c/ack)
                                          ]
                                         [:notify-client
                                          :routing-keys [[:internal :email-to-kramerius.sent]]
                                          :handler (-> h/msg-for-client
                                                       c/from-clojure
                                                       c/to-json
                                                       (c/notify-client :kramerius :export-to-kramerius
                                                                        :with-key :response)
                                                       c/ack
                                                       c/remove-workdir
                                                       )
                                          ]                                         
                                         ;; [:scp
                                         ;;  :routing-keys [[:internal :storage.response]]
                                         ;;  :handler (->
                                         ;;            (comp
                                         ;;             (fn [x]
                                         ;;               (h/scp-to-kramerius x {:scp (fn [from-path to-path]
                                         ;;                                             (str from-path
                                         ;;                                                  "->"
                                         ;;                                                  to-path))})
                                         ;;               )
                                         ;;             (fn [x] (h/prepare-scp-to-kramerius
                                         ;;                     x
                                         ;;                     :import-mount (env :import-mount)
                                         ;;                     :archive-mount (env :archive-mount)
                                         ;;                     :originals-mount (env :originals-mount)
                                         ;;                     )
                                         ;;               )
                                         ;;             )
                                         ;;            c/from-clojure
                                         ;;            c/to-clojure
                                         ;;            (c/send-result-to :kramerius :internal
                                         ;;                              :with-key :scp-package.sent)
                                         ;;            c/ack)
                                         ;;  ]
                                         ;; [:rest
                                         ;;  :routing-keys [[:internal :scp-package.sent]]
                                         ;;  :handler (fn [component ch metadata payload]
                                         ;;             (println "scp-package sent" metadata payload)
                                         ;;             )
                                         ;;  ]
                                         ]
                               }
                              )
                             (component/using {:rabbit :kramerius})
                             (component/using [:kramerius :marcxml2mods :storage])
                             )
   )
  )

