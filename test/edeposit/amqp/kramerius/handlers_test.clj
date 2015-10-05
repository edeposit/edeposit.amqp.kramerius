(ns edeposit.amqp.kramerius.handlers-test
  (:require [clojure.test :refer :all]
            [edeposit.amqp.kramerius.handlers :as h]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.data.xml :as xml]
            [clojure.xml :as x]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]
            [clojure.java.shell :as shell]
            [edeposit.amqp.kramerius.xml.utils :as u]
            [clojurewerkz.serialism.core :as s]
            [clj-time.local :as lt]
            [clj-time.coerce :as tc]
            [clj-time.core :as t]
            )
  )

(deftest with-tmpdir-test
  (testing "add tmpdir to request"
    (let [payload (slurp "resources/export-request.json")
          [[metadata payload] tmpdir] (-> [:no-metadata payload]
                                          h/request-with-tmpdir)
          ]
      (is (.exists tmpdir))
      (is (= metadata :no-metadata))
      (is (= payload (slurp "resources/export-request.json")))
      (fs/delete-dir tmpdir)
      )
    )
  )

(deftest save-request-test
  (testing "save request payload"
    (let [payload (slurp "resources/export-request.json")
          tmpdir (fs/temp-dir "test-export-to-kramerius-request-")
          result (-> [[:no-metadata payload] tmpdir] 
                     h/save-request )
          ]
      (is (.exists tmpdir))
      (is (= tmpdir result))
      (is (.exists (io/file tmpdir "request")))
      (is (.exists (io/file tmpdir "request" "payload.bin")))
      (is (.exists (io/file tmpdir "request" "metadata.clj")))

      (is (.exists (io/file tmpdir "request" "payload")))
      (is (.exists (io/file tmpdir "request" "payload" "uuid")))
      (is (.exists (io/file tmpdir "request" "payload" "aleph_id")))
      (is (.exists (io/file tmpdir "request" "payload" "isbn")))
      (is (.exists (io/file tmpdir "request" "payload" "urnnbn")))
      (is (.exists (io/file tmpdir "request" "payload" "edeposit-url.txt")))
      (is (.exists (io/file tmpdir "request" "payload" "location-at-kramerius")))
      (is (.exists (io/file tmpdir "request" "payload" "is-private")))
      (is (.exists (io/file tmpdir "request" "payload" "oai_marc.xml")))
      (is (.exists (io/file tmpdir "request" "payload" "oai_marc.xml.b64")))

      (is (.exists (io/file tmpdir "request" "payload" "first-page")))
      (is (.exists (io/file tmpdir "request" "payload" "first-page" "robotandbaby_001.jp2")))
      (is (.exists (io/file tmpdir "request" "payload" "first-page" "mimetype")))
      (is (.exists (io/file tmpdir "request" "payload" "first-page" "filename")))

      (is (.exists (io/file tmpdir "request" "payload" "original")))
      (is (.exists (io/file tmpdir "request" "payload" "original" "storage_path")))
      (is (.exists (io/file tmpdir "request" "payload" "original" "mimetype")))
      (is (.exists (io/file tmpdir "request" "payload" "original" "filename")))

      (is (= (fs/size (io/file tmpdir "request" "payload" "oai_marc.xml"))
             (fs/size (io/file "resources" "oai_marc.xml"))))

      (is (= "e65d9072-2c9b-11e5-99fd-b8763f0a3d61" 
             (slurp (io/file tmpdir "request" "payload" "uuid"))))

      (is (= "urn:nbn:cz:mzk-0005ol" 
             (slurp (io/file tmpdir "request" "payload" "urnnbn"))))

      (is (= "80-251-0225-4"
             (slurp (io/file tmpdir "request" "payload" "isbn"))))

      (is (= "cnb001492461" 
             (slurp (io/file tmpdir "request" "payload" "aleph_id"))))

      (is (= "/monografie/2001/John McCarthy/Robot and Baby" 
             (slurp (io/file tmpdir "request" "payload" "location-at-kramerius"))))
       
      (is (= "robotandbaby_001.jp2" 
             (slurp (io/file tmpdir "request" "payload" "first-page" "filename"))))

      (is (= "image/jp2" 
             (slurp (io/file tmpdir "request" "payload" "first-page" "mimetype"))))
      (fs/delete-dir tmpdir)       
      )
    )
  )

(deftest prepare-marcxml2mods-request
  (testing "prepare marcxml2mods request"
    (let [payload (slurp "resources/export-request.json")
          tmpdir (fs/temp-dir "test-export-to-kramerius-request-")
          ]
      (h/save-request [[:no-metadata payload] tmpdir])
      (let [[headers {:keys [:marc_xml :uuid]}] (-> tmpdir 
                                                    h/prepare-marcxml2mods-request)
            ]
        (is (= "e65d9072-2c9b-11e5-99fd-b8763f0a3d61" uuid))
        (is (.exists (io/file tmpdir "marcxml2mods")))
        (is (= (.toString tmpdir) (-> headers :uuid))))
      (fs/delete-dir tmpdir)
      )
    )
  )

(deftest save-marcxml2mods-response-test
   (testing "save marcxml2mods response test"
     (let [payload (slurp "resources/export-request.json")
           tmpdir (fs/temp-dir "test-export-to-kramerius-request-")
           ]

       (h/save-request [[:no-metadata payload] tmpdir])
       (h/prepare-marcxml2mods-request tmpdir)

       (let [ metadata (-> (slurp "resources/communication-with-marcxml2mods/response/metadata.clj")
                           read-string
                           (update-in [:headers] assoc "UUID" (.toString tmpdir)))
             payload (slurp "resources/communication-with-marcxml2mods/response/payload.json")
             [mods_files response-dir] (-> [metadata payload]
                                           h/save-marcxml2mods-response)
             ]
         (is (= tmpdir response-dir))
         (is (.exists (io/file response-dir "marcxml2mods" "response")))
         (is (.exists (io/file response-dir "marcxml2mods" "response" "payload.bin")))
         (is (.exists (io/file response-dir "marcxml2mods" "response" "metadata.clj")))
         (is (= 1 (count mods_files)))
         (is (re-find #"<?xml version=" (-> mods_files (nth 0))))
         )
       (fs/delete-dir tmpdir)
       )
     )
   )

(deftest parse-mods-files-test
  (testing "parse mods files test"
    (let [payload (slurp "resources/export-request.json")
           tmpdir (fs/temp-dir "test-export-to-kramerius-request-")
           ]
      (h/save-request [[:no-metadata payload] tmpdir])
      (h/prepare-marcxml2mods-request tmpdir)
      (let [ metadata (-> (slurp "resources/communication-with-marcxml2mods/response/metadata.clj")
                           read-string
                           (update-in [:headers] assoc "UUID" (.toString tmpdir)))
             payload (slurp "resources/communication-with-marcxml2mods/response/payload.json")
             [mods response-dir] (-> [metadata payload]
                                           h/save-marcxml2mods-response
                                           h/parse-mods-files
                                           )
             ]
        (is (= tmpdir response-dir))
        (isa? vector mods)
        (is (= (count mods) 1))

        (doseq [root mods]
          (is (.contains (u/emit root) "<mods")) 
          (is (= :mods (:tag root)))
          (is (= {:xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance", 
                  :xmlns:xlink "http://www.w3.org/1999/xlink", 
                  :xmlns "http://www.loc.gov/mods/v3", 
                  :version "3.4", 
                  :ID "MODS_VOLUME_0001", 
                  :xsi:schemaLocation "http://www.w3.org/2001/XMLSchema-instance http://www.w3.org/2001/XMLSchema.xsd http://www.loc.gov/mods/v3 http://www.loc.gov/standards/mods/v3/mods-3-4.xsd http://www.w3.org/1999/xlink http://www.w3.org/1999/xlink.xsd"} (:attrs root)))
          )

        (doseq [loc (map zip/xml-zip mods)]
          (is (= "Umění programování v UNIXu"
                 (zx/xml1-> loc :titleInfo :title zx/text)))
          (is (=  "Raymond, Eric S."
                  (zx/xml1-> loc :name :namePart zx/text))))

        )
      (fs/delete-dir tmpdir)
      )
    )
  )

(deftest add-urnnbn-to-mods-test
  (testing "add urnnbn to mods test"
    (let  [payload (slurp "resources/export-request.json")
           tmpdir (fs/temp-dir "test-export-to-kramerius-request-")
           ]
      (h/save-request [[:no-metadata payload] tmpdir])
      (h/prepare-marcxml2mods-request tmpdir)
      (let [ metadata (-> (slurp "resources/communication-with-marcxml2mods/response/metadata.clj")
                          read-string
                          (update-in [:headers] assoc "UUID" (.toString tmpdir)))
            payload (slurp "resources/communication-with-marcxml2mods/response/payload.json")
            [mods response-dir] (-> [metadata payload]
                                    h/save-marcxml2mods-response
                                    h/parse-mods-files
                                    h/add-urnnbn-to-mods
                                    )
            ]
        (is (= tmpdir response-dir))
        (isa? vector mods)
        (is (= (count mods) 1))
        (doseq [root (map zip/xml-zip mods)]
          (is (=  (set (zx/xml-> root :identifier zx/text))
                  #{"urn:nbn:cz:mzk-0005ol" "asd" "cnb001492461" "80-251-0225-4 (brož.)" "85131856"}))
          )
        )
      (fs/delete-dir tmpdir)
      )
    )
  )

(deftest mods->dc-test
   (testing "transformation MODS -> OAI DC"
     (def payload (slurp "resources/export-request.json"))
     (def tmpdir (fs/temp-dir "test-export-to-kramerius-request-"))

     (h/save-request  [[nil payload] tmpdir])

     (let [ metadata (read-string (slurp "resources/communication-with-marcxml2mods/response/metadata.clj"))
           payload (slurp "resources/communication-with-marcxml2mods/response/payload.json")
           actual-metadata (-> metadata (assoc :headers 
                                               (-> metadata :headers (assoc "UUID" (.toString tmpdir)))))
          
           [oai_dcs response-tmpdir] (-> (h/save-marcxml2mods-response [actual-metadata payload])
                                         h/parse-mods-files
                                         h/add-urnnbn-to-mods
                                         h/mods->oai_dcs
                                         )
           ]

       (is (= response-tmpdir tmpdir))

       (isa? vector oai_dcs)
       (is (= (count oai_dcs) 1))
       (let [[root] oai_dcs]
         (is (contains? root :tag))
         (is (contains? root :attrs))
         (is (contains? root :content))
         (let [loc (-> root zip/xml-zip)]
           (is (= (zx/xml1-> loc :dc:title zx/text) "Umění programování v UNIXu"))
           (is (= (zx/xml1-> loc :dc:date zx/text) "2004"))
           (is (=  (set (zx/xml-> loc :dc:identifier zx/text))  
                 #{"urnnbn:urn:nbn:cz:mzk-0005ol" "uuid:asd" "ccnb:cnb001492461" 
                   "isbn:80-251-0225-4 (brož.)" "oclc:85131856"}))
           )
         )
       (fs/delete-dir tmpdir)
       )
     )
   )


(deftest foxml-test
  (testing "make package with FOXML"
    (let  [payload (slurp "resources/export-request.json")
           tmpdir (fs/temp-dir "test-export-to-kramerius-request-")]
      (h/save-request [[:no-metadata payload] tmpdir])
      (h/prepare-marcxml2mods-request tmpdir)
      (let [ metadata (-> (slurp "resources/communication-with-marcxml2mods/response/metadata.clj")
                          read-string
                          (update-in [:headers] assoc "UUID" (.toString tmpdir)))
            payload (slurp "resources/communication-with-marcxml2mods/response/payload.json")
            [mods mods-tmpdir] (-> [metadata payload]
                                    h/save-marcxml2mods-response
                                    h/parse-mods-files
                                    h/add-urnnbn-to-mods)
            [oai_dcs oai-tmpdir] (-> [mods mods-tmpdir] h/mods->oai_dcs)
            [uuid fox-tmpdir] (h/make-package-with-foxml 
                               [mods mods-tmpdir] 
                               [oai_dcs oai-tmpdir] 
                               tmpdir)
            result-dir (io/file fox-tmpdir uuid)
            ]
        
        (is (= fox-tmpdir tmpdir mods-tmpdir))
        (is (.isDirectory (io/file result-dir)))
        (is (.exists      (io/file result-dir "edeposit-url.txt")))
        (is (.isDirectory (io/file result-dir "first-page")))
        (is (.exists (io/file result-dir "first-page" "filename")))
        (is (.exists (io/file result-dir "first-page" "mimetype")))
        (is (= (slurp (io/file (io/resource "robotandbaby_001.jp2")))
               (slurp (io/file result-dir "first-page" "robotandbaby_001.jp2"))))

        (let [loc (-> (io/file result-dir (str uuid ".xml"))
                      io/input-stream
                      x/parse
                      zip/xml-zip
                      )]
          (let [ref (zx/xml1-> loc :foxml:datastream [(zx/attr= :ID "IMG_FULL")]
                               :foxml:datastreamVersion :foxml:contentLocation (zx/attr :REF)) 
                storage_path (slurp (io/file tmpdir "request" "payload" "original" "storage_path"))]
            (is (= ref (str "file:" storage_path))))
          (let [ref (zx/xml1-> loc :foxml:datastream [(zx/attr= :ID "IMG_PREVIEW")] 
                               :foxml:datastreamVersion :foxml:contentLocation (zx/attr :REF)) 
                filename (slurp (io/file tmpdir "request" "payload" "first-page" "filename"))]
            (is (=  ref (str "file:first-page/" filename)))
            )
          )
        )
      (fs/delete-dir tmpdir)
      )
    )
  )

(deftest zip-package-test
  (testing "make zip package"
    (let  [payload (slurp "resources/export-request.json")
           tmpdir (fs/temp-dir "test-export-to-kramerius-request-")]
      (h/save-request [[:no-metadata payload] tmpdir])
      (h/prepare-marcxml2mods-request tmpdir)
      (let [ metadata (-> (slurp "resources/communication-with-marcxml2mods/response/metadata.clj")
                          read-string
                          (update-in [:headers] assoc "UUID" (.toString tmpdir)))
            payload (slurp "resources/communication-with-marcxml2mods/response/payload.json")
            [mods mods-tmpdir] (-> [metadata payload]
                                    h/save-marcxml2mods-response
                                    h/parse-mods-files
                                    h/add-urnnbn-to-mods)
            [oai_dcs oai-tmpdir] (-> [mods mods-tmpdir] h/mods->oai_dcs)
            [out-file uuid zip-tmpdir] (-> (h/make-package-with-foxml 
                                            [mods mods-tmpdir] 
                                            [oai_dcs oai-tmpdir] 
                                            tmpdir)
                                           h/make-zip-package
                                           )
            ]
        (is (= zip-tmpdir tmpdir))
        (is (.exists out-file))
        (let [result (->  (shell/sh "zip" "-T" (.toString out-file)) :out)]
          (is (= (re-find #" OK$" result) " OK")))
        )
      (fs/delete-dir tmpdir)
      )
    )
  )

(deftest prepare-export-to-storage-request
  (testing "prepare request for export to storage"
    (let  [payload (slurp "resources/export-request.json")
           tmpdir (fs/temp-dir "test-export-to-kramerius-request-")]
      (h/save-request [[:no-metadata payload] tmpdir])
      (h/prepare-marcxml2mods-request tmpdir)
      (let [ metadata (-> (slurp "resources/communication-with-marcxml2mods/response/metadata.clj")
                          read-string
                          (update-in [:headers] assoc "UUID" (.toString tmpdir)))
            payload (slurp "resources/communication-with-marcxml2mods/response/payload.json")
            [mods mods-tmpdir] (-> [metadata payload]
                                    h/save-marcxml2mods-response
                                    h/parse-mods-files
                                    h/add-urnnbn-to-mods)
            [oai_dcs oai-tmpdir] (-> [mods mods-tmpdir] h/mods->oai_dcs)
            [metadata payload request-workdir] (-> (h/make-package-with-foxml 
                                                    [mods mods-tmpdir] 
                                                    [oai_dcs oai-tmpdir] 
                                                    tmpdir)
                                                   h/make-zip-package
                                                   h/prepare-request-for-export-to-storage
                                                   )
            ]
        (is (= request-workdir tmpdir))
        (is (.isDirectory (io/file request-workdir "export-to-storage")))
        (is (.isDirectory (io/file request-workdir "export-to-storage" "request")))
   
        (let [zip-file-b64 (io/file request-workdir 
                                    (str (slurp (io/file tmpdir "request" "payload" "uuid")) ".zip" ".b64"))]
          (is (.exists zip-file-b64))
          (let [request-dir (io/file request-workdir "export-to-storage" "request")
                deserialized-metadata (s/deserialize (slurp (io/file request-dir "metadata.clj"))
                                                     s/clojure-content-type)
                deserialized-payload (s/deserialize (slurp (io/file request-dir "payload.bin"))
                                                    s/json-content-type)
                ]
            (is (= deserialized-metadata metadata))
            (is (= deserialized-payload payload))
            (is (= (.toString tmpdir) (-> deserialized-metadata :headers (get "UUID"))))
            (is (= "80-251-0225-4" (-> deserialized-payload :isbn)))
            (is (= "cnb001492461" (-> deserialized-payload :aleph_id)))
            (is (= (slurp zip-file-b64) (-> deserialized-payload :b64_data)))
            )
          )
        )
      (fs/delete-dir tmpdir)
      )
    )
  )

(deftest save-response-from-storage-test
  (testing "save response from storage test"
    (let [workdir (fs/temp-dir "test-export-to-kramerius-storage-response-")
          payload (.getBytes (slurp "resources/communication-with-storage/response/payload.bin"))
          metadata (-> (slurp "resources/communication-with-storage/response/metadata.clj")
                       read-string
                       (update-in [:headers] assoc "UUID" (.toString workdir)))]
      (let [ [msg response-workdir] (h/save-response-from-export-to-storage metadata payload)]
        (is (= workdir response-workdir))
        (is (.exists (io/file response-workdir)))
        (is (.exists (io/file response-workdir "export-to-storage")))
        (is (.exists (io/file response-workdir "export-to-storage" "response")))
        (is (.exists (io/file response-workdir "export-to-storage" "response" "payload")))

        (is (= "/monografie/01/02/aa-ofsdfasfs" (-> msg :dir_pointer)))
        (is (= "e65d9072-2c9b-11e5-99fd-b8763f0a3d61" (-> msg :uuid)))
        (is (= "cnb001492461" (-> msg :aleph_id)))
        (is (= "80-251-0225-4" (-> msg :isbn)))
        (is (= "" (-> msg :b64_data)))
        )
      (fs/delete-dir workdir)
      )
    )
  )

(deftest prepare-scp-to-kramerius-test
  (testing "prepare scp to kramerius test"
    (let     [payload (slurp "resources/export-request.json")
              workdir (fs/temp-dir "test-export-to-kramerius-request-")]
      (h/save-request [[:no-metadata payload] workdir])
      (h/prepare-marcxml2mods-request workdir)
      (let [ metadata (-> (slurp "resources/communication-with-marcxml2mods/response/metadata.clj")
                          read-string
                          (update-in [:headers] assoc "UUID" (.toString workdir)))
            payload (slurp "resources/communication-with-marcxml2mods/response/payload.json")
            [mods mods-tmpdir] (-> [metadata payload]
                                   h/save-marcxml2mods-response
                                   h/parse-mods-files
                                   h/add-urnnbn-to-mods)
            [oai_dcs oai-tmpdir] (-> [mods mods-tmpdir] h/mods->oai_dcs)
            [_ _ request-workdir] (-> (h/make-package-with-foxml   [mods mods-tmpdir] 
                                                                   [oai_dcs oai-tmpdir] 
                                                                   workdir)
                                      h/make-zip-package
                                      h/prepare-request-for-export-to-storage)]
        (let [payload (.getBytes (slurp "resources/communication-with-storage/response/payload.bin"))
              metadata (-> (slurp "resources/communication-with-storage/response/metadata.clj")
                           read-string
                           (update-in [:headers] assoc "UUID" (.toString workdir)))]
          (let [ [scp-workdir] (-> (h/save-response-from-export-to-storage metadata payload)
                                   (h/prepare-scp-to-kramerius
                                    :import-mount "/var/edeposit_import"
                                    :archive-mount "/var/edeposit_archive"
                                    :originals-mount "/var/edeposit_originals"))
                 uuid (slurp (io/file workdir "request" "payload" "uuid"))]
            (is (.exists (io/file scp-workdir)))
            (is (.exists (io/file scp-workdir "scp-to-kramerius")))
            (is (.exists (io/file scp-workdir "scp-to-kramerius" "dir_pointer")))
            (is (.exists (io/file scp-workdir "scp-to-kramerius" "request")))
            (is (.exists (io/file scp-workdir "scp-to-kramerius" "request" (str uuid ".xml"))))
            (is (.exists (io/file scp-workdir "scp-to-kramerius" "request" "from")))
            (is (= (.toString (io/file scp-workdir "scp-to-kramerius" "request" (str uuid ".xml")))
                   (slurp  (io/file scp-workdir "scp-to-kramerius" "request" "from"))))

            (is (.exists (io/file scp-workdir "scp-to-kramerius" "request" "to")))
            (is (= (str "/var/edeposit_import/" uuid ".xml")
                   (slurp (io/file scp-workdir "scp-to-kramerius" "request" "to"))))

            (let [dir_pointer (slurp (io/file scp-workdir "scp-to-kramerius" "dir_pointer"))
                  loc (-> (io/file scp-workdir "scp-to-kramerius" "request" (str uuid ".xml"))
                          io/input-stream
                          x/parse
                          zip/xml-zip)]
              (is (= "/monografie/01/02/aa-ofsdfasfs" dir_pointer))
              (is (= (str "file:///var/edeposit_originals/monografie/01/01-robotandbaby.pdf")
                     (zx/xml1-> loc :foxml:datastream [(zx/attr= :ID "IMG_FULL")] 
                                :foxml:datastreamVersion
                                :foxml:contentLocation (zx/attr :REF))))
              (is (= (str "file:///var/edeposit_archive" dir_pointer "/first-page/robotandbaby_001.jp2")
                     (zx/xml1-> loc :foxml:datastream [(zx/attr= :ID "IMG_PREVIEW")]
                                :foxml:datastreamVersion
                                :foxml:contentLocation (zx/attr :REF))))
              )
            )
          )
        )
      (fs/delete-dir workdir)
      )
    )
  )

(deftest scp-to-kramerius-test
  (testing "scp to kramerius test"
    (let     [local-now (lt/local-now)
              payload (slurp "resources/export-request.json")
              workdir (fs/temp-dir "test-export-to-kramerius-request-")]
      (h/save-request [[:no-metadata payload] workdir])
      (h/prepare-marcxml2mods-request workdir)
      (let [ metadata (-> (slurp "resources/communication-with-marcxml2mods/response/metadata.clj")
                          read-string
                          (update-in [:headers] assoc "UUID" (.toString workdir)))
            payload (slurp "resources/communication-with-marcxml2mods/response/payload.json")
            [mods mods-tmpdir] (-> [metadata payload]
                                   h/save-marcxml2mods-response
                                   h/parse-mods-files
                                   h/add-urnnbn-to-mods)
            [oai_dcs oai-tmpdir] (-> [mods mods-tmpdir] h/mods->oai_dcs)
            [_ _ request-workdir] (-> (h/make-package-with-foxml   [mods mods-tmpdir] 
                                                                   [oai_dcs oai-tmpdir] 
                                                                   workdir)
                                      h/make-zip-package
                                      h/prepare-request-for-export-to-storage)]
        (let [payload (.getBytes (slurp "resources/communication-with-storage/response/payload.bin"))
              metadata (-> (slurp "resources/communication-with-storage/response/metadata.clj")
                           read-string
                           (update-in [:headers] assoc "UUID" (.toString workdir)))]
          (let [ [scp-workdir] (-> (h/save-response-from-export-to-storage metadata payload)
                                   (h/prepare-scp-to-kramerius  :import-mount "/var/edeposit_import"
                                                                :archive-mount "/var/edeposit_archive"
                                                                :originals-mount "/var/edeposit_originals")
                                   (h/scp-to-kramerius {:scp (fn [from-path to-path] (str from-path
                                                                                         "->"
                                                                                         to-path))})
                                   )
                 uuid (slurp (io/file workdir "request" "payload" "uuid"))]
            (is (.exists (io/file scp-workdir)))
            (let [response-dir (io/file scp-workdir "scp-to-kramerius" "response")]
              (is (.exists response-dir))
              (is (.exists (io/file response-dir "result")))
              (is (.exists (io/file response-dir "sent")))
              (let [result (slurp (io/file response-dir "result"))]
                (is (re-find #"/var/edeposit_import" result ))
                (is (re-find #"->" result))
                (is (re-find #"scp-to-kramerius/request" result)))
              (let [sent (tc/from-string (slurp (io/file response-dir "sent")))]
                (is sent)
                (is (t/before? local-now sent))
                )
              )
            )
          )
        )
      (fs/delete-dir workdir)
      )
    )
  )


