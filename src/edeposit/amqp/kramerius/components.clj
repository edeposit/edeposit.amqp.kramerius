(ns edeposit.amqp.kramerius.components
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojurewerkz.serialism.core :as s]
            [com.stuartsierra.component :as component]
            [edeposit.amqp.kramerius.handlers :as h]
            [me.raynes.fs :as fs]
            [langohr.basic     :as lb]
            [langohr.channel :as lch]
            [langohr.consumers :as lc]
            [langohr.core :as lcor]
            [langohr.exchange  :as lx]
            [langohr.queue     :as lq])
  )

(comment ;; hook for emacs
  (add-hook 'after-save-hook 'restart-app nil t)
  )

(defmethod print-dup java.io.File
  [f out]
  (.write out (str "#=" `(java.io.File. ~(.toString f)))))

(defn raw-pass
  [handler]
  (fn [[metadata payload]]
     (handler [metadata payload])
    )
  )

(defn to-clojure
  [handler]
  (fn [metadata payload]
    (let [new-payload (handler [metadata payload])]
      [metadata (s/serialize new-payload s/clojure-content-type)]
      )
    )
  )

(defn from-clojure
  [handler]
  (fn [[metadata payload]]
    (handler (s/deserialize payload s/clojure-content-type))
    )
  )

(defn from-json
  "provide payload to handler. And calls the handler."
  [handler]
  (fn [[metadata payload]]
    (handler (s/deserialize payload s/json-content-type))
    )
  )

(defn to-json
  "provide payload to handler. And calls the handler."
  [handler]
  (fn [metadata payload]
    (let [[new-metadata new-payload] (handler [metadata payload])]
      [(assoc new-metadata :content-encoding "application/json")
       (s/serialize new-payload s/json-utf8-content-type)
       ]
      )
    )
  )

(defn send-result-to
  "creates handler that takes a response,
  serialize it and sends it into exchange with routing key"
  [handler rabbit exchange & {:keys [with-key serializer]}]
  (fn [component ch metadata payload]
    (let [[new-metadata new-payload] (handler metadata payload)]
      (log/info "publishing new message ->" exchange with-key)
      (lb/publish (-> component rabbit :ch)
                  (name exchange)
                  (name with-key)
                  new-payload
                  (assoc new-metadata :persistent true))
      [new-metadata new-payload]
      )
    )
  )

(defn ack
  [handler]
  (fn [component ch metadata payload]
    (let [[new-metadata new-payload] (handler component ch metadata payload)]
      (log/info "message ack")
      (lb/ack ch (:delivery-tag metadata))
      [new-metadata new-payload]
      )
    )
  )

(defn remove-workdir
  [handler]
  (fn [component ch metadata payload]
    (let [[new-metadata new-payload] (handler component ch metadata payload)]
      (let [workdir (-> metadata :headers (get "UUID") (.toString) io/file)]
        (log/info "workdir removed" workdir)
        (fs/delete-dir workdir)
        [new-metadata new-payload]
        )
      )
    )
  )

(defn notify-client
  [handler rabbit exchange & {:keys [with-key msg]}]
  (fn [component ch metadata payload]
    (let [[new-metadata new-payload] (handler metadata payload)]
      (log/info "notify client about export result")
      (lb/publish (-> component rabbit :ch)
                  (name exchange)
                  (name with-key)
                  new-payload
                  (assoc new-metadata :persistent true)
                  )
      [new-metadata new-payload]
      )
    )
  )

(defrecord Rabbit [uri conn ch]
  component/Lifecycle
  (start [component]
    (let [conn (lcor/connect {:uri uri})
          ch   (lch/open conn)]
      (assoc component :conn conn :ch ch)))
  
  (stop [component]
    (lcor/close ch)
    (lcor/close conn)
    component))

(defn rabbit-mq [uri]
  (map->Rabbit {:uri uri}))

(defrecord AMQP-Middleware [definition]
  component/Lifecycle
  (start [this]
    (let [ch (-> this :rabbit :ch)]
      (doseq [exchange (map name (:exchanges definition))]
        (log/info "declaring topic exchange: " exchange)
        (lx/topic ch exchange {:durable true}))
      (doseq [[qname & {:keys [routing-keys]}] (:queues definition)]
        (log/info "declaring queue: " (name qname))
        (lq/declare ch (name qname) {:durable true :auto-delete false})
        (doseq [[exchange routing-key] routing-keys]
          (log/info "declaring queue binding for queue" qname ":" exchange routing-key)
          (lq/bind ch (name qname) (name exchange) {:routing-key (name routing-key)})))
      (doseq [[qname & {:keys [handler]}] (:queues definition)]
        (log/info "activating handler for queue:" (name qname))
        (let [consumer (lc/create-default ch {:handle-delivery-fn (partial handler this)})]
          (lb/consume ch (name qname) consumer {:auto-ack false})))
        )
    this
    )

  (stop [this]
    this
    )  
  )

(defn amqp-middleware [definition]
  (map->AMQP-Middleware {:definition definition}))
