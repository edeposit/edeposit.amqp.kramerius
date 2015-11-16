(ns edeposit.amqp.kramerius.components-test
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.test :refer :all]
            [clojure.test :refer (deftest is are testing)]
            [edeposit.amqp.kramerius.components :as c]
            [edeposit.amqp.kramerius.handlers :as h]
            [langohr.basic :as lb]
            [langohr.consumers :as lc]
            )
  )

(deftest kramerius-amqp-config-test
  (testing "configure RabbitMQ internal structures"
    (let [ marcxml2mods-rabbit (-> "amqp://guest:guest@localhost/marcxml"
                                   c/rabbit-mq
                                   (.start))
          
          rabbit (->  "amqp://guest:guest@localhost/kramerius"
                      c/rabbit-mq
                      (.start)
                      )
          ]
      (let [
            marcxml2mods (-> marcxml2mods-rabbit
                             (c/amqp-middleware
                              {:exchanges [:convert-to-mods]
                               :queues [
                                        [:kramerius
                                         :routing-keys [[:convert-to-mods :response]]
                                         :handler (-> h/save-marcxml2mods-response
                                                      c/raw-pass
                                                      c/to-clojure
                                                      (c/send-result-to rabbit :internal
                                                                        :with-key :marcxml2mods.response)
                                                      c/ack)
                                         ]
                                        ]
                               })
                             (.start))
            
            kramerius (-> rabbit
                          (c/amqp-middleware
                           {:exchanges [:export-to-kramerius :internal]
                            :queues [[:request-saver
                                      :routing-keys [[:export-to-kramerius :request]]
                                      :handler (-> (comp h/save-request
                                                         h/request-with-tmpdir)
                                                   c/raw-pass
                                                   c/to-clojure
                                                   (c/send-result-to rabbit :internal
                                                                     :with-key :request-save.response)
                                                   c/ack)
                                      ]
                                     [:preview-page-maker
                                      :routing-keys [[:internal :request-save.response]]
                                      :handler (-> h/make-preview-page
                                                   c/from-clojure
                                                   c/to-clojure
                                                   (c/send-result-to rabbit :internal
                                                                     :with-key :make-preview.response)
                                                   c/ack)
                                      ]
                                     [:marcxml2mods
                                      :routing-keys [[:internal :request-save.response]]
                                      :handler (-> h/prepare-marcxml2mods-request
                                                   c/from-clojure
                                                   c/to-json
                                                   (c/send-result-to marcxml2mods-rabbit :convert-to-mods
                                                                     :with-key :request)
                                                   c/ack)
                                      ]
                                     [:mods
                                      :routing-keys [[:internal :marcxml2mods.response]]
                                      :handler (-> (comp h/add-urnnbn-to-mods
                                                         h/parse-mods-files)
                                                   c/from-clojure
                                                   c/to-clojure
                                                   (c/send-result-to rabbit :internal
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
                                                   (c/send-result-to rabbit :internal
                                                                     :with-key :foxml-package.created)
                                                   )
                                      ]
                                     ]
                            }
                           )
                          (.start))
            ]
        (lb/publish (:ch rabbit) "export-to-kramerius" "request"
                    (-> "export-request.json" io/resource io/file slurp)
                    (-> "request-metadata.clj" io/resource io/file slurp read-string))
        
        (let [ch (:ch marcxml2mods-rabbit)
              consumer (lc/create-default
                        ch
                        {:handle-delivery-fn
                         (fn [ch metadata ^bytes payload]
                           (let [uuid (-> metadata :headers (get "UUID") (.toString))
                                 new-payload (-> "communication-with-marcxml2mods/response/payload.json"
                                                 io/resource io/file slurp)
                                 new-metadata (-> "communication-with-marcxml2mods/response/metadata.clj"
                                                  io/resource io/file slurp
                                                  read-string
                                                  (update-in [:headers] assoc "UUID" uuid)
                                                  )
                                 ]
                             (lb/publish ch "convert-to-mods" "response" new-payload new-metadata)
                             )
                           )}
                        )
              ]
          (lb/consume ch "converter" consumer {:auto-ack false})
          )

        (Thread/sleep 2000)
        (.stop kramerius)
        (.stop rabbit)
        )
      )
    )
  )
