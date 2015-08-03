(ns edeposit.amqp.kramerius.handlers
  (:require [langohr.basic     :as lb]
            [me.raynes.fs :as fs]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.pprint :as pp]
            [clojure.data.json :as json]
            [clojure.data.xml :as xml]
            [clj-time.core :as t]
            [edeposit.amqp.kramerius.xml.mods :as mods]
            [edeposit.amqp.kramerius.xml.foxml :as f]
            [clojure.java.shell :as shell]
            )
  (:import [org.apache.commons.codec.binary Base64])
  )

; Michal Merka 736 539 653
(comment ;; hook for emacs
  (add-hook 'after-save-hook 'restart-app nil t)
)

(defn request-with-tmpdir
  "It creates tmpdir and add it to request data"
  [[metadata payload]]
  (let [tmpdir (fs/temp-dir "test-export-to-kramerius-request-")]
    [[metadata payload] tmpdir]))

(defn save-request
  "it returns name of a directory where payload is saved"
  [[[metadata payload] workdir]]
  (def msg (json/read-str (String. payload) :key-fn keyword))
  (def marcxml-file (io/file workdir "oai_marc.xml"))

  (log/info "save export request to" workdir)  
  (spit (-> marcxml-file .toString (.concat ".b64")) (:b64_marcxml msg))
  (spit (io/file workdir "uuid") (:uuid msg))
  (spit (io/file workdir "urnnbn") (:urnnbn msg))

  (with-open [out (io/output-stream marcxml-file)]
    (.write out (Base64/decodeBase64 (:b64_marcxml msg))))

  (let [out-dir (io/file workdir "img_full")
        data (-> msg :img_full)
        ]
    (.mkdir out-dir)
    (spit (io/file out-dir "mimetype") (-> data :mimetype))
    (spit (io/file out-dir "filename") (-> data :filename))
    (with-open [out (io/output-stream (io/file out-dir (-> data :filename)))]
      (.write out (Base64/decodeBase64 (-> data :b64_data)))))

  (let [out-dir (io/file workdir "img_preview")
        data (-> msg :img_preview)]
    (.mkdir out-dir)
    (spit (io/file out-dir "mimetype") (-> data :mimetype))
    (spit (io/file out-dir "filename") (-> data :filename))
    (with-open [out (io/output-stream (io/file out-dir (-> data :filename)))]
      (.write out (Base64/decodeBase64 (-> data :b64_data)))))

  workdir)

(defn prepare-marcxml2mods-request
  [workdir]
  (log/info "prepare marcxml2mods request at" workdir)
  [{:uuid (.toString workdir)}
   {:marc_xml (slurp (io/file workdir "oai_marc.xml"))
    :uuid (slurp (io/file workdir "uuid"))
    }])

(defn save-marcxml2mods-response
  [[metadata payload]]
  (let [uuid (-> metadata :headers (get "UUID"))
        workdir (io/file uuid)]
    (log/info "save marcxml2mods response into" workdir)
    (spit (io/file workdir "marcxml2mods-response-metadata.clj") metadata)
    (spit (io/file workdir "marcxml2mods-response-payload.json") payload)
    (let [ mods_files (-> payload 
         (json/read-str :key-fn keyword) 
         :mods_files)]
      [mods_files workdir])))

(defn parse-mods-files
  "takes strings and returns xml root for each xml string"
  [[mods_files workdir]]
  [(map xml/parse-str mods_files) workdir])

(defn save-img-files
  [[mods_files workdir]]
  (let [uuid (slurp (io/file workdir "uuid"))
        slurp-img-full (comp slurp (partial io/file workdir "img_full"))
        slurp-img-preview (comp slurp (partial io/file workdir "img_preview"))
        ]
    (.mkdir (io/file workdir uuid))
    (.mkdir (io/file workdir uuid "img"))
    (fs/copy (io/file workdir "img_full" (slurp-img-full "filename")) 
             (io/file workdir uuid "img" (slurp-img-full "filename")))
    (fs/copy (io/file workdir "img_preview" (slurp-img-preview "filename")) 
             (io/file workdir uuid "img" (slurp-img-preview "filename")))
    [{:img-type :img_full
      :mime-type (slurp-img-full "mimetype")
      :filename (slurp-img-full "filename")
      }
     {:img-type :img_preview
      :mime-type (slurp-img-preview "mimetype")
      :filename (slurp-img-preview "filename")
      } 
     workdir]))

(defn add-urnnbn-to-first-mods
  [[mods workdir]]
  (let [urnnbn (slurp (io/file workdir "urnnbn"))]
    [(into [(mods/with-urnnbn-identifier (first mods) urnnbn)] (rest mods))  workdir]))

(defn mods->oai_dcs
  [[mods workdir]]
  [(map mods/mods->oai_dc mods) workdir])


(defn make-foxml
  [[mods workdir] [oai_dcs workdir-1] [full-file preview-file workdir-2] fedora-import-dir]
  {:pre [(= workdir workdir-1 workdir-2)]}
  (let [uuid (slurp (io/file workdir "uuid"))
        foxml-dir (io/file workdir uuid)
        ]
    (.mkdir (io/file workdir uuid "xml"))
    (let [foxml (f/foxml mods oai_dcs full-file preview-file
                         {:uuid uuid 
                          :label "ahoj" 
                          :created (t/now)
                          :last-modified (t/now)
                          :fedora-import-dir fedora-import-dir
                          })
          out-file (io/file workdir uuid "xml" (str uuid ".xml"))
          ]
      ;(pp/pprint foxml)
      (with-open [out (io/writer out-file)]
        (xml/emit foxml out))
      [uuid workdir])))

(defn make-zip-package
  [[uuid workdir]]
  (let [out-file (io/file workdir (str uuid ".zip"))]
    (shell/sh "zip" "-r" (.toString out-file) uuid :dir (.toString workdir))
    [out-file workdir]
    )
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
