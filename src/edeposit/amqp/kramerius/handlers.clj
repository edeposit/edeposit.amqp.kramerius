(ns edeposit.amqp.kramerius.handlers
  (:require [langohr.basic     :as lb]
            [me.raynes.fs :as fs]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.pprint :as pp]
            )
  (:import [org.apache.commons.codec.binary Base64])
  )

(comment ;; hook for emacs
  (add-hook 'after-save-hook 'restart-app nil t)
)

(defn request-with-tmpdir
  "It creates tmpdir and add it to request data"
  [[metadata payload]]
  (let [tmpdir (fs/temp-dir "test-export-to-kramerius-request-")]
    [[metadata payload] tmpdir]
    )
  )

(defn save-request
  "it returns name of a directory where payload is saved"
  [[[metadata payload] workdir]]
  (def msg (json/read-str (String. payload) :key-fn keyword))
  (def marcxml-file (io/file workdir "oai_marc.xml"))
  (def data-file (->> (:filename msg)
                     io/file
                     .getName
                     (io/file workdir)
                     )
    )

  (log/info "save export request to" workdir)  
  (spit (-> marcxml-file .toString (.concat ".b64")) (:b64_marcxml msg))
  (spit (-> data-file .toString (.concat ".b64")) (:b64_data msg))
  (spit (io/file workdir "uuid") (:uuid msg))
  (spit (io/file workdir "filename") (:filename  msg))

  (with-open [out (io/output-stream marcxml-file)]
    (.write out (Base64/decodeBase64 (:b64_marcxml msg))))
  (with-open [out (io/output-stream data-file)]
    (.write out (Base64/decodeBase64 (:b64_data msg))))

  workdir
  )

(defn prepare-marcxml2mods-request
  [workdir]
  (log/info "prepare marcxml2mods request at" workdir)
  [{:uuid (.toString workdir)}
   {:marc_xml (slurp (io/file workdir "oai_marc.xml"))
    :uuid (slurp (io/file workdir "uuid"))
    }
   ]
  )

(defn save-marcxml2mods-response
  [[metadata payload]]
  (let [uuid (-> metadata :headers (get "UUID"))
        workdir (io/file uuid)]
    (log/info "save marcxml2mods response into" workdir)
    (spit (io/file workdir "marcxml2mods-response-metadata.clj") metadata)
    (spit (io/file workdir "marcxml2mods-response-payload.json") payload)

    workdir
    )
  )

(defn make-foxml
  [workdir]
  )

(defn parse-and-export [metadata ^bytes payload]
  (let [msg (json/read-str (String. payload) :key-fn keyword) 
        data-file (fs/temp-file "kramerius-amqp-" ".foxml")
        ]
    (with-open [out (io/output-stream data-file)]
      (.write out (Base64/decodeBase64 (:b64_data msg)))
      )
    (comment
      (let [result (validate (.toString data-file))]
       (.delete data-file)
       result
       )
      )
    )
  )

(def repl-out *out*)
(defn prn-to-repl [& args]
  (binding [*out* repl-out]
    (apply prn args)))

(defn response-properties [metadata]
  {:headers {"UUID" (-> metadata :headers (get "UUID"))}
   :content-type "edeposit/export-to-kramerius-response"
   :content-encoding "application/json"
   :persistent true
   }
  )

(defn handle-delivery [kramerius metadata ^bytes payload]
  (log/info "new message arrived")

  (defn send-response [msg]
    (lb/publish (:channel kramerius) (:exchange kramerius) "response" msg (response-properties metadata))
    )
  (-> (parse-and-export metadata payload)
      (json/write-str)
      (send-response)
      )
  (lb/ack (:channel kramerius) (:delivery-tag metadata))
  (log/info "message ack")
  )
