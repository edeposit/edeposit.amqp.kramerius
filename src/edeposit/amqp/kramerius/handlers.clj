(ns edeposit.amqp.kramerius.handlers
  (:require [clj-time.core :as t]
            [clj-time.local :as lt]
            [clojure.data.json :as json]
            [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.pprint :as pp]
            [clojure.string :as cs]
            [clojure.tools.logging :as log]
            [clojurewerkz.serialism.core :as s]
            [clojure.xml :as x]
            [edeposit.amqp.kramerius.xml.foxml :as f]
            [edeposit.amqp.kramerius.xml.mods :as m]
            [edeposit.amqp.kramerius.xml.utils :as u]
            [edeposit.amqp.kramerius.email.utils :as e]
            [langohr.basic     :as lb]
            [postal.message :as pm]
            [me.raynes.fs :as fs])
  (:import [org.apache.commons.codec.binary Base64])
  )

; Michal Merka 736 539 653
(comment ;; hook for emacs
  (add-hook 'after-save-hook 'restart-app nil t)
)

(defn workdir-create
  "Create a temporary file or dir, trying n times before giving up."
  [basedir prefix suffix]
  (loop [tries 10]
    (let [tmp (io/file basedir (fs/temp-name prefix suffix))]
      (when (pos? tries)
        (if (fs/mkdirs tmp)
          tmp
          (recur (dec tries))))))
  )

(defn request-with-tmpdir
  "It creates tmpdir and add it to request data"
  [workdir-prefix]
  (fn
    [[metadata payload]]
    (log/info "received new message")
    (let [tmpdir (workdir-create workdir-prefix "export-to-kramerius-request-" "")]
      (log/info "handlers will user dir" (.toString tmpdir))
      [[metadata payload] tmpdir])
    )
  )

(defn save-request
  "it returns name of a directory where payload is saved"
  [[[metadata payload] workdir]]
  (log/info "saving request at" (.toString workdir))
  (when (not (= :no-dir workdir))
    (fs/mkdirs (io/file workdir "request" "payload"))
    (spit (io/file workdir "request" "payload.bin") (String. payload))
    (spit (io/file workdir "request" "metadata.clj") metadata)
    (let [msg (json/read-str (String. payload) :key-fn keyword) 
          marcxml-file (io/file workdir "request" "payload" "oai_marc.xml")]
      (spit (-> marcxml-file .toString (.concat ".b64")) (:b64_marcxml msg))
      (spit (io/file workdir "request" "payload" "uuid") (:uuid msg))
      (spit (io/file workdir "request" "payload" "urnnbn") (:urnnbn msg))
      (spit (io/file workdir "request" "payload" "aleph_id") (:aleph_id msg))
      (spit (io/file workdir "request" "payload" "isbn") (:isbn msg))
      (spit (io/file workdir "request" "payload" "location-at-kramerius") (:location_at_kramerius msg))
      (spit (io/file workdir "request" "payload" "is-private") (:is_private msg))
      (spit (io/file workdir "request" "payload" "edeposit-url.txt") (:edeposit_url msg))
      (spit (io/file workdir "request" "payload" "preview-page-position") (:first_page_position msg))
      
      (with-open [out (io/output-stream marcxml-file)]
        (.write out (Base64/decodeBase64 (:b64_marcxml msg))))

      (let [preview-page-dir (io/file workdir "request" "payload" "preview-page")
            preview-page-filename (str (cs/replace (-> msg :original :filename) #"\.[^\.]*$" "") "_001.jp2")]
        (fs/mkdirs preview-page-dir)
        (spit (io/file preview-page-dir "filename") preview-page-filename)
        (spit (io/file preview-page-dir "mimetype") "image/jp2"))

      (let [out-dir (io/file workdir "request" "payload" "original")
            original (-> msg :original)]
        (.mkdir out-dir)
        (spit (io/file out-dir "filename") (-> original :filename))
        (spit (io/file out-dir "storage_path") (-> original :storage_path))
        (spit (io/file out-dir "mimetype") (-> original :mimetype))
        (with-open [out (io/output-stream (io/file out-dir (-> original :filename)))]
          (.write out (Base64/decodeBase64 (-> original :b64_data))))
        )
      )
    )
  workdir
  )

(defn make-preview-page
  [workdir]
  "from saved signal"
  "pdftk - first page
  gm convert pdf -> jp2000"
  (when (not (= :no-dir workdir))
    (let [payload-dir (io/file workdir "request" "payload")
          in (io/file payload-dir "original" (slurp (io/file payload-dir "original" "filename")))
          preview (io/file payload-dir "preview-page" (slurp (io/file payload-dir "preview-page" "filename")))
          preview-base (str (cs/replace (.toString preview) #"\.[^\.]+$" "") "-preview")
          preview-page-position (slurp (io/file payload-dir "preview-page-position"))]
      (let [output (-> (shell/sh "pdftk" (.toString in)
                                 "cat"   preview-page-position
                                 "output" (str preview-base ".pdf") "verbose") :out)]
        (when (not (and (re-find #"Command Line Data is valid\." output)
                        (re-find #"Adding page [0-9]" output)))
          (throw (Exception. (str "problem with running pdftk:" output))))
        (let [result (-> (shell/sh "gm" "convert" (str preview-base ".pdf") (.toString preview)))]
          (when (not (= (:exit result) 0))
            (throw (Exception. (str "converting preview file to jp2: " (prn-str result)))))
          (let [result (-> (shell/sh "gm" "identify" (.toString preview)))]
            (when (not (.contains (:out result) " JP2 "))
              (throw (Exception. (str "preview file is not identified ad JP2: " (prn-str result)))))
            )
          )
        )
      )
    )
  workdir
  )

(defn prepare-marcxml2mods-request
  [workdir]
  (log/info "prepare marcxml2mods request at" workdir)
  (fs/mkdirs (io/file workdir "marcxml2mods" "request"))
  [{:headers {"UUID" (.toString workdir)}
    :content-type "edeposit/marcxml2mods-request"}
   {:marc_xml (slurp (io/file workdir "request" "payload" "oai_marc.xml"))
    :uuid (slurp (io/file workdir "request" "payload" "uuid"))
    }]
  )

(defn save-marcxml2mods-response
  [[metadata payload]]
  (let [uuid (-> metadata :headers (get "UUID") (.toString))
        workdir (io/file uuid)]
    (log/info "save marcxml2mods response into" workdir)
    (fs/mkdirs (io/file workdir "marcxml2mods" "response"))
    (let [response-dir (io/file workdir "marcxml2mods" "response")]
      (spit (io/file response-dir "metadata.clj") metadata)
      (spit (io/file response-dir "payload.bin") payload))
    (let [ mods_files (-> (String. payload)
                          (json/read-str :key-fn keyword) 
                          :mods_files)]
      [mods_files workdir]))
  )

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
  (if (= :no-dir workdir)
    [[] :no-dir]
    (let [urnnbn (slurp (io/file workdir "request" "payload" "urnnbn"))]
      [(map (fn [one-mods] (m/with-urnnbn-identifier one-mods urnnbn)) mods) workdir])
    )
  )

(defn mods->oai_dcs
  [[mods workdir]]
  [(map m/mods->oai_dc mods) workdir]
  )

(defn with-oai_dcs
  [[mods workdir]]
  [[mods workdir] (mods->oai_dcs [mods workdir]) workdir]
  )

(defn make-package-with-foxml
  [[mods mods-workdir] [oai_dcs oai-workdir] workdir]
  {:pre [(= workdir mods-workdir oai-workdir)]}
  (let [payload-dir (io/file workdir "request" "payload")
        uuid (slurp (io/file payload-dir "uuid"))
        result-dir (io/file workdir uuid)
        full-file {:mimetype (slurp (io/file payload-dir "original" "mimetype"))
                   :filename (slurp (io/file payload-dir "original" "filename"))
                   :storage_path (slurp (io/file payload-dir "original" "storage_path"))
                   }
        preview-file {:mimetype (slurp (io/file payload-dir "preview-page" "mimetype"))
                      :filename (slurp (io/file payload-dir "preview-page" "filename"))
                      }
        ]
    (.mkdir result-dir)
    (let [foxml (f/foxml mods oai_dcs full-file preview-file
                         {:uuid uuid 
                          :label "ahoj"
                          :created (lt/local-now)
                          :last-modified (lt/local-now)
                          })
          out-file (io/file result-dir (str uuid ".xml"))
          ]
      (spit out-file (u/emit foxml))
      (fs/copy-dir (io/file workdir "request" "payload" "preview-page") result-dir)
      (fs/copy (io/file workdir "request" "payload" "edeposit-url.txt") 
               (io/file result-dir "edeposit-url.txt"))
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

  (fs/mkdirs (io/file workdir "export-to-storage" "request"))
  (let [zip-file-b64 (io/file (str (.toString zip-file) ".b64"))]
    (with-open [out (io/output-stream zip-file-b64 )]
      (.write out (Base64/encodeBase64 (.getBytes (slurp zip-file)))))
    (let [request-dir (io/file workdir "export-to-storage" "request")
          metadata {:headers { "UUID" (.toString workdir)}
                    :content-type "edeposit/export-to-storage-request"
                    :content-encoding "application/json"
                    :persistent true}
          payload {:isbn (slurp (io/file workdir "request" "payload" "isbn"))
                   :uuid uuid
                   :aleph_id (slurp (io/file workdir "request" "payload" "aleph_id"))
                   :b64_data (slurp zip-file-b64)
                   :dir_pointer ""}
          ]
      (spit (io/file request-dir "metadata.clj") (s/serialize metadata s/clojure-content-type))
      (spit (io/file request-dir "payload.bin") (s/serialize payload s/json-content-type))
      [metadata payload workdir]
      )
    )
  )

(defn save-response-from-export-to-storage
  [[metadata payload]]
  (let [msg (json/read-str (String. payload) :key-fn keyword)
        workdir (io/file (-> metadata :headers (get "UUID") (.toString)))]
    (if (.exists workdir)
      (let [response-dir (io/file workdir "export-to-storage" "response")]
        (fs/mkdirs (io/file response-dir "payload"))
        (spit (io/file response-dir "metadata.clj") metadata
              ;(s/serialize metadata s/clojure-content-type)
              )
        (io/copy payload (io/file response-dir "payload.bin"))
        [msg workdir])
      (do
        (log/error "workdir:" (.toString workdir) "doesnot exists!")
        [msg nil])
      )
    )
  )

(defn prepare-scp-to-kramerius
  [[{:keys [dir_pointer] :as msg} workdir] & {:keys [import-mount archive-mount originals-mount]}]
  {:pre [(some? import-mount)
         (some? archive-mount)
         (some? originals-mount)
         ]}
  (let [uuid (slurp (io/file workdir "request" "payload" "uuid"))]
    (let [request-dir (io/file workdir "scp-to-kramerius" "request")
          foxml-fname (str uuid ".xml")]
      (fs/mkdirs request-dir)
      (spit (io/file workdir "scp-to-kramerius" "dir_pointer") dir_pointer)
      (spit (io/file request-dir "from") (.toString (io/file request-dir foxml-fname)))
      (spit (io/file request-dir "to")
            (str "/" 
                 (cs/join "/" 
                          (map (fn [path] (-> path 
                                             (cs/replace #"^file:" "") 
                                             (cs/replace #"^[/]+"  "")))
                               [import-mount foxml-fname]))))
      (let [root (-> (io/file workdir uuid foxml-fname)
                     io/input-stream
                     x/parse)]
        (-> root
            (f/update-rels archive-mount dir_pointer originals-mount)
            u/emit
            (io/copy (io/file request-dir foxml-fname)))
        )
      )
    [workdir]
    )
  )

(defn scp-to-kramerius
  [[workdir] {:keys [scp]}]
  "scp is a function: (fn [from-path to-path])"
  (let [out-dir (io/file workdir "scp-to-kramerius" "response")
        request-dir (io/file workdir "scp-to-kramerius" "request")
        to-path (slurp (io/file request-dir "to"))
        from-path (slurp (io/file request-dir "from"))
        ]
    (fs/mkdirs out-dir)
    (let [result (scp from-path to-path)
          sent (.toString (lt/local-now))
          ]
      (spit (io/file out-dir "result") result)
      (spit (io/file out-dir "sent") sent)
      [workdir :result result :sent sent]
      )
    )
  )

(defn prepare-email
  [[{:keys [dir_pointer] :as msg} workdir] & {:keys [import-mount archive-mount originals-mount]}]
  {:pre [(some? import-mount)
         (some? archive-mount)
         (some? originals-mount)
         ]}
  (let [uuid (slurp (io/file workdir "request" "payload" "uuid"))]
    (let [request-dir (io/file workdir "email-for-kramerius" "request")
          foxml-fname (str uuid ".xml")]
      (fs/mkdirs request-dir)
      (spit (io/file workdir "email-for-kramerius" "dir_pointer") dir_pointer)
      (let [root (-> (io/file workdir uuid foxml-fname)
                     io/input-stream
                     x/parse)]
        (-> root
            (f/update-rels archive-mount dir_pointer originals-mount)
            u/emit
            (io/copy (io/file request-dir foxml-fname)))
        )
      )
    workdir
    )
  )

(defn make-email
  [& {:keys [from to]}]
  (fn [workdir]
    (let [subject "eDeposit: balicek k importu do Krameria"
          from-payload (comp slurp (partial io/file workdir "request" "payload"))
          body [ {:type "text/html; charset=utf-8"
                  :content (e/body-with-table "Balíček k importu do Krameria"
                                              "Dobrý den, posíláme balíček k importu do Krameria. Údaje o ePublikaci:"
                                              {:aleph-sysnumber (from-payload "aleph_id")
                                               :isbn (from-payload "isbn")
                                               :edeposit-url (from-payload "edeposit-url.txt")
                                               :is-private (from-payload "is-private")
                                               :location-at-kramerius (from-payload "location-at-kramerius")
                                               :urnnbn (from-payload  "urnnbn")
                                               :uuid (from-payload  "uuid")
                                               :original-filename (from-payload "original" "filename")
                                               :original-storage-path (from-payload "original" "storage_path")
                                               }
                                              )
                  }
                {:type :attachment
                 :content (io/file workdir "email-for-kramerius" "request" (str (from-payload "uuid") ".xml"))
                 }
                ]
          
          ]
      [workdir {:from from :to to  :subject subject :body body}]
      )
    )
  )

(defn save-email-at-workdir
  [[workdir msg]]
  (let [out-dir (io/file workdir "email-for-kramerius")]
    (fs/mkdirs out-dir)
    (spit (io/file out-dir "email-with-package.eml") (pm/message->str msg))
    )
  [workdir msg]
  )

(defn sendmail
  [mailer]
  (fn [[workdir {:keys [from to subject body] :as args}]]
    (let [result (mailer args)]
      [workdir result]
      )
    )
  )
(defn msg-for-client
  [[workdir result]]
  (let [metadata {:headers {"UUID" (-> workdir (io/file "request" "payload" "uuid") slurp)}
                  :content-type "edeposit/export-to-kramerius-response" }
        payload "export to Kramerius succeeded"]
    [metadata payload]
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
