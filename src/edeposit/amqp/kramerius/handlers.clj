(ns edeposit.amqp.kramerius.handlers
  (:require [me.raynes.fs :as fs]
            [langohr.basic     :as lb]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.pprint :as pp]
            [clojure.data.json :as json]
            [clojure.data.xml :as xml]
            [clj-time.core :as t]
            [edeposit.amqp.kramerius.xml.mods :as m]
            [edeposit.amqp.kramerius.xml.foxml :as f]
            [edeposit.amqp.kramerius.xml.utils :as u]
            [clojure.java.shell :as shell]
            [clojurewerkz.serialism.core :as s]
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
  (let [tmpdir (fs/temp-dir "export-to-kramerius-request-")]
    [[metadata payload] tmpdir]))

(defn save-request
  "it returns name of a directory where payload is saved"
  [[[metadata payload] workdir]]
  (spit (io/file workdir "payload.bin") payload)
  (.mkdir (io/file workdir "payload"))
  (spit (io/file workdir "metadata.clj") metadata)
  (let [msg (json/read-str (String. payload) :key-fn keyword) 
        marcxml-file (io/file  workdir "payload" "oai_marc.xml")]
    (log/info "save export request to" workdir)  
    (spit (-> marcxml-file .toString (.concat ".b64")) (:b64_marcxml msg))
    (spit (io/file workdir "payload" "uuid") (:uuid msg))
    (spit (io/file workdir "payload" "urnnbn") (:urnnbn msg))
    (spit (io/file workdir "payload" "aleph_id") (:aleph_id msg))
    (spit (io/file workdir "payload" "isbn") (:isbn msg))
    (spit (io/file workdir "payload" "location-at-kramerius") (:location_at_kramerius msg))
    (spit (io/file workdir "payload" "is-private") (:is_private msg))
    (spit (io/file workdir "payload" "edeposit-url.txt") (:edeposit_url msg))
    (with-open [out (io/output-stream marcxml-file)]
      (.write out (Base64/decodeBase64 (:b64_marcxml msg))))
    
    (let [out-dir (io/file workdir "payload" "first-page")
          first-page (-> msg :first_page)
          ]
      (.mkdir out-dir)
      (spit (io/file out-dir "mimetype") (-> first-page :mimetype))
      (spit (io/file out-dir "filename") (-> first-page :filename))
      (with-open [out (io/output-stream (io/file out-dir (-> first-page :filename)))]
        (.write out (Base64/decodeBase64 (-> first-page :b64_data)))))
    
    (let [out-dir (io/file workdir "payload" "original")
          original (-> msg :original)
          ]
      (.mkdir out-dir)
      (spit (io/file out-dir "mimetype") (-> original :mimetype))
      (spit (io/file out-dir "filename") (-> original :filename))
      (spit (io/file out-dir "storage_path") (-> original :storage_path)))
    )
  workdir
  )

(defn prepare-marcxml2mods-request
  [workdir]
  
  (log/info "prepare marcxml2mods request at" workdir)

  (.mkdir (io/file workdir "marcxml2mods"))
  (.mkdir (io/file workdir "marcxml2mods" "request"))

  [{:uuid (.toString workdir)}
   {:marc_xml (slurp (io/file workdir "payload" "oai_marc.xml"))
    :uuid (slurp (io/file workdir "payload" "uuid"))
    }])

(defn save-marcxml2mods-response
  [[metadata payload]]
  (let [uuid (-> metadata :headers (get "UUID"))
        workdir (io/file uuid)]
    (log/info "save marcxml2mods response into" workdir)

    (.mkdir (io/file workdir "marcxml2mods"))
    (.mkdir (io/file workdir "marcxml2mods" "response"))
    (let [response-dir (io/file workdir "marcxml2mods" "response")]
      (spit (io/file response-dir "metadata.clj") metadata)
      (spit (io/file response-dir "payload.bin") payload)
      )
    (let [ mods_files (-> payload 
                          (json/read-str :key-fn keyword) 
                          :mods_files)]
      [mods_files workdir])))



(defn parse-mods-files
  "takes strings and returns xml root for each xml string"
  [[mods_files workdir]]
  [(map (fn [mods] (-> mods 
                      xml/parse-str
                      (update-in [:attrs] assoc :xmlns "http://www.loc.gov/mods/v3")
                      (update-in [:attrs] assoc :xmlns:xlink "http://www.w3.org/1999/xlink")
                      (update-in [:attrs] assoc :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance")
                      (update-in [:attrs] (fn [attrs] (assoc attrs 
                                                            :xsi:schemaLocation 
                                                            (:xsi/schemaLocation attrs))))
                      (update-in [:attrs] dissoc :xsi/schemaLocation)
                      ))  mods_files) workdir])

(defn add-urnnbn-to-mods
  [[mods workdir]]
  (let [urnnbn (slurp (io/file workdir "payload" "urnnbn"))]
    [(map (fn [one-mods] (m/with-urnnbn-identifier one-mods urnnbn)) mods) workdir]))

(defn mods->oai_dcs
  [[mods workdir]]
  [(map m/mods->oai_dc mods) workdir])

;; (defn save-img-files
;;   [[mods_files workdir]]
;;   (let [uuid (slurp (io/file workdir "uuid"))
;;         slurp-img-full (comp slurp (partial io/file workdir "img_full"))
;;         slurp-img-preview (comp slurp (partial io/file workdir "img_preview"))
;;         ]
;;     (.mkdir (io/file workdir uuid))
;;     (.mkdir (io/file workdir uuid "img"))
;;     (fs/copy (io/file workdir "img_full" (slurp-img-full "filename")) 
;;              (io/file workdir uuid "img" (slurp-img-full "filename")))
;;     (fs/copy (io/file workdir "img_preview" (slurp-img-preview "filename")) 
;;              (io/file workdir uuid "img" (slurp-img-preview "filename")))
;;     [{:type :img_full
;;       :mimetype (slurp-img-full "mimetype")
;;       :filename (slurp-img-full "filename")
;;       }
;;      {:type :img_preview
;;       :mimetype (slurp-img-preview "mimetype")
;;       :filename (slurp-img-preview "filename")
;;       } 
;;      workdir]))


(defn make-package-with-foxml
  [[mods mods-workdir] [oai_dcs oai-workdir] workdir & {:keys [fedora-import-dir storage-dir]} ]
  {:pre [(= workdir mods-workdir oai-workdir)]}
  (let [payload-dir (io/file workdir "payload")
        uuid (slurp (io/file payload-dir "uuid"))
        result-dir (io/file workdir uuid)
        full-file {:mimetype (slurp (io/file payload-dir "original" "mimetype"))
                   :filename (slurp (io/file payload-dir "original" "filename"))
                   :storage_path (slurp (io/file payload-dir "original" "storage_path"))
                   }
        preview-file {:mimetype (slurp (io/file payload-dir "first-page" "mimetype"))
                      :filename (slurp (io/file payload-dir "first-page" "filename"))
                      }
        ]
    (.mkdir result-dir)
    (let [foxml (f/foxml mods oai_dcs full-file preview-file
                         {:uuid uuid 
                          :label "ahoj"
                          :created (t/now)
                          :last-modified (t/now)
                          :fedora-import-dir fedora-import-dir
                          :storage-dir storage-dir
                          })
          out-file (io/file result-dir (str uuid ".xml"))
          ]
      (spit out-file (u/emit foxml))
      (fs/copy-dir (io/file workdir "payload" "first-page") result-dir)
      (fs/copy (io/file workdir "payload" "edeposit-url.txt") (io/file result-dir "edeposit-url.txt"))
      [uuid workdir])))

(defn make-zip-package
  [[uuid workdir]]
  (let [out-file (io/file workdir (str uuid ".zip"))]
    (shell/sh "zip" "-r" (.toString out-file) uuid :dir (.toString workdir))
    [out-file uuid workdir]
    )
  )

(defn prepare-request-for-export-to-storage
  [[zip-file uuid workdir]]

  (.mkdir (io/file workdir "export-to-storage"))
  (.mkdir (io/file workdir "export-to-storage" "request"))

  (let [request-dir (io/file workdir "export-to-storage" "request")
        metadata {:headers {"UUID" uuid}
                  :content-type "edeposit/export-to-storage-request"
                  :content-encoding "application/json"
                  :persistent true}
        payload {:isbn (slurp (io/file workdir "payload" "isbn"))
                 :uuid uuid
                 :aleph_id (slurp (io/file workdir "payload" "aleph_id"))
                 :b64_data ""
                 :dir_pointer ""}
        ]
    (spit (io/file request-dir "metadata.clj") (s/serialize metadata s/clojure-content-type))
    [metadata payload workdir]
    )
  )

(defn parse-and-export [metadata ^bytes payload]
  (let [msg (json/read-str (String. payload) :key-fn keyword) 
        data-file (fs/temp-file "kramerius-amqp-" ".foxml")
        ]
    (with-open [out (io/output-stream data-file)]
      (.write out (Base64/decodeBase64 (:b64_data msg)))
      )
    )
  )

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
