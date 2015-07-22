(ns edeposit.amqp.kramerius.handlers
  (:require [langohr.basic     :as lb]
            [me.raynes.fs :as fs]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            )
  (:import [org.apache.commons.codec.binary Base64])
  )

(comment ;; hook for emacs
  (add-hook 'after-save-hook 'restart-app nil t)
)

(defn save-request-payload 
  "it returns name of a directory where payload is saved"
  [metadata payload]
  (def msg (json/read-str (String. payload) :key-fn keyword))
  (def tmpdir (fs/temp-dir "export-to-kramerius-request-"))
  (def marcxml-file (io/file tmpdir "marcxml.xml"))
  (def data-file (->> (:filename msg)
                     io/file
                     .getName
                     (io/file tmpdir)
                     )
    )
  (.concat (.toString data-file) ".b64")
  (spit marcxml-file (Base64/decodeBase64 (:b64_marcxml msg)))
  (spit (-> marcxml-file .toString (.concat ".b64")) (:b64_marcxml msg))
  (spit data-file (Base64/decodeBase64 (:b64_data msg)))
  (spit (-> data-file .toString (.concat ".b64")) (:b64_data msg))
  (spit (io/file tmpdir "uuid") (:uuid msg))
  (spit (io/file tmpdir "filename") (:filename  msg))
  tmpdir
  )

(defn message-id 
  "Message ID is the same as workdir name. It is the easiest way to distinct messages."
  [workdir]
  (-> tmpdir .toString)
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










