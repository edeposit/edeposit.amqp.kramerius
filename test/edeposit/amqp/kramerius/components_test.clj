(ns edeposit.amqp.kramerius.components-test
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.test :refer :all]
            [clojure.test :refer (deftest is are testing)]
            [edeposit.amqp.kramerius.components :as c]
            [edeposit.amqp.kramerius.handlers :as h]
            [langohr.basic :as lb]
            [langohr.core :as l]
            [langohr.http :as lh]
            [langohr.consumers :as lc]
            [langohr.exchange :as le]
            [langohr.channel :as lch]
            [langohr.queue :as lq]
            [clojure.java.shell :as sh]
            [postal.core :as pc]
            )
  )

(when (not (.exists (io/file "/usr" "sbin" "rabbitmqctl")))
  (do
    (doseq [ vhost ["marcxml" "kramerius" "storage"]]
      (lh/add-vhost vhost)
      (lh/set-permissions vhost "guest" {:configure ".*" :write ".*" :read ".*"}))
    (deftest kramerius-amqp-config-test
      (testing "configure RabbitMQ internal structures"
        (let [ connection {:marcxml2mods (-> "amqp://guest:guest@localhost/marcxml"
                                             c/rabbit-mq
                                             (.start))                       
                           :kramerius  (->  "amqp://guest:guest@localhost/kramerius"
                                            c/rabbit-mq
                                            (.start))
                           :storage (->  "amqp://guest:guest@localhost/storage"
                                         c/rabbit-mq
                                         (.start))
                           }
              ]
          (let [ch (-> :marcxml2mods connection :ch)]
            (lq/declare ch "converter" {:durable true :auto-delete false})
            (le/topic ch "convert-to-mods" {:durable true})
            (lq/bind ch "converter"  "convert-to-mods" {:routing-key "request" :durable true}))
          (let [ch (-> :storage connection :ch)]
            (lq/declare ch "daemon" {:durable true :auto-delete false})
            (le/topic ch "export" {:durable true})
            (lq/bind ch "daemon" "export" {:routing-key "request" :durable true}))
          (let [
                marcxml2mods (-> (c/amqp-middleware
                                  {:exchanges [:convert-to-mods]
                                   :queues [
                                            [:kramerius
                                             :routing-keys [[:convert-to-mods :response]]
                                             :handler (-> h/save-marcxml2mods-response
                                                          c/raw-pass
                                                          c/to-clojure
                                                          (c/send-result-to :kramerius :internal
                                                                            :with-key :marcxml2mods.response)
                                                          c/ack)
                                             ]
                                            ]
                                   })
                                 (assoc :rabbit (connection :marcxml2mods))
                                 (assoc :kramerius  (connection :kramerius))
                                 (.start))
                
                storage (-> (c/amqp-middleware
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
                              })
                            (assoc :rabbit (connection :storage))
                            (assoc :kramerius  (connection :kramerius))
                            (.start))
                
                kramerius (-> (c/amqp-middleware
                               {:exchanges [:export-to-kramerius :internal]
                                :queues [[:request-saver
                                          :routing-keys [[:export-to-kramerius :request]]
                                          :handler (-> (comp h/save-request
                                                             (h/request-with-tmpdir "/tmp"))
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
                                                                         :convert-to-mods
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
                                                     ;;(h/sendmail pc/send-message)
                                                     (h/sendmail (fn [msg]
                                                                   {:code 0,
                                                                    :error :SUCCESS,
                                                                    :message "message sent"}))
                                                     h/save-email-at-workdir
                                                     (h/make-email :from "edeposit@edeposit.cz"
                                                                   :to "stavel.jan@gmail.com")
                                                     (fn [x] (h/prepare-email
                                                             x
                                                             :import-mount "/var/edeposit_import"
                                                             :archive-mount "/var/edeposit_archive"
                                                             :originals-mount "/var/edeposit_originals"
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
                                         ;;                     :import-mount "/var/edeposit_import"
                                         ;;                     :archive-mount "/var/edeposit_archive"
                                         ;;                     :originals-mount "/var/edeposit_originals"
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
                              (assoc :rabbit       (connection :kramerius))
                              (assoc :kramerius    (connection :kramerius))
                              (assoc :marcxml2mods (connection :marcxml2mods))
                              (assoc :storage      (connection :storage))
                              (.start))
                ]
            ((->>  {:handle-delivery-fn
                    (fn [ch metadata ^bytes payload]
                      (let [uuid (-> metadata :headers (get "UUID") (.toString))
                            new-payload (-> "communication-with-marcxml2mods/response/payload.json"
                                            io/resource io/file slurp)
                            new-metadata (-> "communication-with-marcxml2mods/response/metadata.clj"
                                             io/resource io/file slurp
                                             read-string
                                             (update-in [:headers] assoc "UUID" uuid))
                            ]
                        (lb/publish ch "convert-to-mods" "response" new-payload new-metadata)
                        )
                      )}
                   (lc/create-default (:ch (connection :marcxml2mods)))
                   (partial lb/consume (:ch (connection :marcxml2mods)) "converter"))
             {:auto-ack true})
            ((->>  {:handle-delivery-fn
                    (fn [ch metadata ^bytes payload]
                      (let [uuid (-> metadata :headers (get "UUID") (.toString))
                            new-payload (-> "communication-with-storage/response/payload.bin"
                                            io/resource io/file slurp)
                            new-metadata (-> "communication-with-storage/response/metadata.clj"
                                             io/resource io/file slurp
                                             read-string
                                             (update-in [:headers] assoc "UUID" uuid))
                            ]
                        (lb/publish ch "export" "response" new-payload new-metadata)
                        )
                      )}
                   (lc/create-default (:ch (connection :storage)))
                   (partial lb/consume (:ch (connection :storage)) "daemon"))
             {:auto-ack true})
            
            (lb/publish (:ch (connection :kramerius)) "export-to-kramerius" "request"
                        (-> "export-request.json" io/resource io/file slurp)
                        (-> "request-metadata.clj" io/resource io/file slurp read-string))
            
            (Thread/sleep 4000)
            
            (.stop (connection :kramerius))
            (.stop (connection :marcxml2mods))
            (.stop (connection :storage))
            )
          )
        )
      )
    )
  )

