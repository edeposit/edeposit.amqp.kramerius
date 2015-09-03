(ns edeposit.amqp.kramerius.handlers-test
  (:require [clojure.test :refer :all]
            [edeposit.amqp.kramerius.handlers :as h]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.data.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]
            [clojure.java.shell :as shell]
            [edeposit.amqp.kramerius.xml.utils :as u]
            )
  )

(comment
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
)

(comment
(deftest save-request-test
  (testing "save request payload"
    (let [payload (slurp "resources/export-request.json")
          tmpdir (fs/temp-dir "test-export-to-kramerius-request-")
          result (-> [[:no-metadata payload] tmpdir] 
                     h/save-request )
          ]
      (is (.exists tmpdir))
      (is (= tmpdir result))
      (is (.exists (io/file tmpdir "payload.bin")))
      (is (.exists (io/file tmpdir "metadata.clj")))

      (is (.exists (io/file tmpdir "payload")))
      (is (.exists (io/file tmpdir "payload" "uuid")))
      (is (.exists (io/file tmpdir "payload" "urnnbn")))
      (is (.exists (io/file tmpdir "payload" "location-at-kramerius")))
      (is (.exists (io/file tmpdir "payload" "is-private")))
      (is (.exists (io/file tmpdir "payload" "oai_marc.xml")))
      (is (.exists (io/file tmpdir "payload" "oai_marc.xml.b64")))
      (is (.exists (io/file tmpdir "payload" "first-page")))
      (is (.exists (io/file tmpdir "payload" "first-page" "robotandbaby_001.jp2")))
      (is (.exists (io/file tmpdir "payload" "first-page" "mimetype")))
      (is (.exists (io/file tmpdir "payload" "first-page" "filename")))

      (is (= (fs/size (io/file tmpdir "payload" "oai_marc.xml"))
             (fs/size (io/file "resources" "oai_marc.xml"))))

      (is (= "e65d9072-2c9b-11e5-99fd-b8763f0a3d61" 
             (slurp (io/file tmpdir "payload" "uuid"))))

      (is (= "urn:nbn:cz:mzk-0005ol" 
             (slurp (io/file tmpdir "payload" "urnnbn"))))

      (is (= "/monografie/2001/John McCarthy/Robot and Baby" 
             (slurp (io/file tmpdir "payload" "location-at-kramerius"))))
       
      (is (= "robotandbaby_001.jp2" 
             (slurp (io/file tmpdir "payload" "first-page" "filename"))))

      (is (= "image/jp2" 
             (slurp (io/file tmpdir "payload" "first-page" "mimetype"))))
      (fs/delete-dir tmpdir)       
      )
    )
  )
)

(comment
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
)

(comment
(deftest save-marcxml2mods-response-test
   (testing "save marcxml2mods response test"
     (let [payload (slurp "resources/export-request.json")
           tmpdir (fs/temp-dir "test-export-to-kramerius-request-")
           ]

       (h/save-request [[:no-metadata payload] tmpdir])
       (h/prepare-marcxml2mods-request tmpdir)

       (let [ metadata (-> (slurp "resources/marcxml2mods-response-metadata.clj")
                           read-string
                           (update-in [:headers] assoc "UUID" (.toString tmpdir)))
             payload (slurp "resources/marcxml2mods-response-payload.json")
             [mods_files response-dir] (-> [metadata payload]
                                           h/save-marcxml2mods-response)
             ]
         (is (= tmpdir response-dir))
         (is (.exists (io/file response-dir "marcxml2mods" "response")))
         (is (.exists (io/file response-dir "marcxml2mods" "response" "payload.bin")))
         (is (.exists (io/file response-dir "marcxml2mods" "response" "metadata.clj")))
         (is (= 1 (count mods_files)))
         (is (re-find #"<?xml version=" (-> mods_files (nth 0))))
         ;(println mods_files)
         )
       (fs/delete-dir tmpdir)
       )
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
      (let [ metadata (-> (slurp "resources/marcxml2mods-response-metadata.clj")
                           read-string
                           (update-in [:headers] assoc "UUID" (.toString tmpdir)))
             payload (slurp "resources/marcxml2mods-response-payload.json")
             [mods response-dir] (-> [metadata payload]
                                           h/save-marcxml2mods-response
                                           h/parse-mods-files
                                           )
             ]
        (is (= tmpdir response-dir))
        (isa? vector mods)
        (is (= (count mods) 1))
        (doseq [root (map zip/xml-zip mods)]
          (is (= "Umění programování v UNIXu"
                 (zx/xml1-> root :mods:titleInfo :mods:title zx/text)))
          (is (=  "Raymond, Eric S."
                  (zx/xml1-> root :mods:name :mods:namePart zx/text))))
        (is (.contains (with-out-str (u/emit (-> mods (nth 0))))  "<mods:mods")))
      (fs/delete-dir tmpdir)
      )
    )
  )

(comment
(deftest add-urnnbn-to-mods-test
  (testing "add urnnbn to mods test"
    (let  [payload (slurp "resources/export-request.json")
           tmpdir (fs/temp-dir "test-export-to-kramerius-request-")
           ]
      (h/save-request [[:no-metadata payload] tmpdir])
      (h/prepare-marcxml2mods-request tmpdir)
      (let [ metadata (-> (slurp "resources/marcxml2mods-response-metadata.clj")
                          read-string
                          (update-in [:headers] assoc "UUID" (.toString tmpdir)))
            payload (slurp "resources/marcxml2mods-response-payload.json")
            [mods response-dir] (-> [metadata payload]
                                    h/save-marcxml2mods-response
                                    h/parse-mods-files
                                    h/add-urnnbn-to-mods
                                    )
            ]
        (is (= tmpdir response-dir))
        (isa? vector mods)
        (is (= (count mods) 1))
        ;(println (xml/emit-str (-> mods (nth 0))))
        ;(println (-> mods (nth 0)))
        (doseq [root (map zip/xml-zip mods)]
          (is (=  (set (zx/xml-> root :dc:identifier zx/text))
                  #{"urnnbn:urn:nbn:cz:mzk-0005ol" "uuid:asd" "ccnb:cnb001492461" 
                    "isbn:80-251-0225-4 (brož.)" "oclc:85131856"}))
          )
        )
      )
    )
  )
)


(comment
(deftest mods->dc-test
   (testing "transformation MODS -> OAI DC"
     (def payload (slurp "resources/export-request.json"))
     (def tmpdir (fs/temp-dir "test-export-to-kramerius-request-"))

     (h/save-request  [[nil payload] tmpdir])

     (let [ metadata (read-string (slurp "resources/marcxml2mods-response-metadata.clj"))
           payload (slurp "resources/marcxml2mods-response-payload.json")
           actual-metadata (-> metadata (assoc :headers 
                                               (-> metadata :headers (assoc "UUID" (.toString tmpdir)))))
          
           [oai_dcs response-tmpdir] (-> (h/save-marcxml2mods-response [actual-metadata payload])
                                         h/parse-mods-files
                                         h/add-urnnbn-to-first-mods
                                         h/mods->oai_dcs
                                         )
           ]

       (is (= (.toString response-tmpdir)
              (.toString tmpdir)
              ))

       (isa? vector oai_dcs)
       (is (= (count oai_dcs) 1))
      
       (doseq [root (map (comp zip/xml-zip xml/sexp-as-element) oai_dcs)]
         (is (=  (zx/xml1-> root :dc:title  zx/text)  "Umění programování v UNIXu"))
         (is (=  (zx/xml1-> root :dc:date zx/text) "2004"))
         (is (=  (set (zx/xml-> root :dc:identifier zx/text))  
                 #{"urnnbn:urn:nbn:cz:mzk-0005ol" "uuid:asd" "ccnb:cnb001492461" 
                   "isbn:80-251-0225-4 (brož.)" "oclc:85131856"}))))

     (fs/delete-dir tmpdir))
   )
)

(comment
(deftest save-img-files-test
  (testing "save img files to FOXML dir"
    (def payload (slurp "resources/export-request.json"))
    (def tmpdir (fs/temp-dir "test-export-to-kramerius-request-"))

    (h/save-request  [[nil payload] tmpdir])

    (let [metadata (read-string (slurp "resources/marcxml2mods-response-metadata.clj"))
          payload (slurp "resources/marcxml2mods-response-payload.json")
          actual-metadata (-> metadata (assoc :headers 
                                              (-> metadata :headers (assoc "UUID" (.toString tmpdir)))))

          [full-file preview-file result-tmpdir] 
          (-> (h/parse-mods-files (h/save-marcxml2mods-response [actual-metadata payload]))
              h/save-img-files)

          uuid (slurp (io/file result-tmpdir "uuid"))]

      (is (= (.toString result-tmpdir) (.toString tmpdir)))
      (is (.isDirectory (io/file result-tmpdir uuid)))
      (is (.isDirectory (io/file result-tmpdir uuid "img")))

      (is (.exists (io/file result-tmpdir uuid "img" (-> full-file :filename))))
      (is (= :img_full (-> full-file :img-type)))
      (is (= "application/pdf" (-> full-file :mime-type)))
      
      (is (.exists (io/file result-tmpdir uuid "img" (-> preview-file :filename))))
      (is (= :img_preview (-> preview-file :img-type)))
      (is (= "image/jpeg" (-> preview-file :mime-type))))
    
    (fs/delete-dir tmpdir)
    )
  )
)

(comment
(deftest foxml-test
  (testing "make FOXML"
    (def payload (slurp "resources/export-request.json"))
    (def tmpdir (fs/temp-dir "test-export-to-kramerius-request-"))

    (h/save-request  [[nil payload] tmpdir])

    (let [ metadata (read-string (slurp "resources/marcxml2mods-response-metadata.clj"))
          payload (slurp "resources/marcxml2mods-response-payload.json")
          actual-metadata (-> metadata (assoc :headers 
                                              (-> metadata :headers (assoc "UUID" (.toString tmpdir)))))
          [mods tmpdir-0] (h/parse-mods-files (h/save-marcxml2mods-response [actual-metadata payload]))
          [oai_dcs tmpdir-1] (h/mods->oai_dcs [mods tmpdir-0])
          [full-file preview-file tmpdir-2] (h/save-img-files [mods tmpdir-0])
          [fname result-tmpdir] (h/make-foxml [mods tmpdir-0] [oai_dcs tmpdir-1] [full-file preview-file tmpdir-2] "/var/fedora/import/")
          uuid (slurp (io/file result-tmpdir "uuid"))
          ]

      (is (= (.toString result-tmpdir)
             (.toString tmpdir)))
      (is (.exists (io/file result-tmpdir uuid)))
      (is (.isDirectory (io/file result-tmpdir uuid)))
      (is (.isDirectory (io/file result-tmpdir uuid "xml")))

      (let [root (-> (io/file result-tmpdir uuid "xml" (str uuid ".xml"))
                     io/input-stream
                     xml/parse
                     zip/xml-zip
                     )]
        ;(def root (-> (io/file "/tmp/aa.xml") io/input-stream xml/parse zip/xml-zip))
        (let [ref (zx/xml1-> root :datastream [(zx/attr= :ID "IMG_FULL")] :datastreamVersion :contentLocation (zx/attr :REF)) ]
          (is (= (.toString (io/file "file:/var/fedora/import/" uuid "img" (-> full-file :filename))) ref))
          )
        (let [ref (zx/xml1-> root :datastream [(zx/attr= :ID "IMG_PREVIEW")] :datastreamVersion :contentLocation (zx/attr :REF)) ]
          (is (= (.toString (io/file "file:/var/fedora/import/" uuid "img" (-> preview-file :filename))) ref))
          )
        )
      )
    (fs/delete-dir tmpdir)
    )
  )
)


(comment
(deftest zip-package-test
  (testing "make zip package"
    (def payload (slurp "resources/export-request.json"))
    (def tmpdir (fs/temp-dir "test-export-to-kramerius-request-"))

    (h/save-request  [[nil payload] tmpdir])

    (let [ metadata (read-string (slurp "resources/marcxml2mods-response-metadata.clj"))
          payload (slurp "resources/marcxml2mods-response-payload.json")
          actual-metadata (-> metadata (assoc :headers 
                                              (-> metadata :headers (assoc "UUID" (.toString tmpdir)))))
          [mods tmpdir-0] (h/parse-mods-files (h/save-marcxml2mods-response [actual-metadata payload]))
          [oai_dcs tmpdir-1] (h/mods->oai_dcs [mods tmpdir-0])
          [full-file preview-file tmpdir-2] (h/save-img-files [mods tmpdir-0])
          [out-file result-tmpdir] (-> 
                                 (h/make-foxml [mods tmpdir-0] 
                                               [oai_dcs tmpdir-1] 
                                               [full-file preview-file tmpdir-2] 
                                               "/var/fedora/import/")
                                 h/make-zip-package
                                 )
          uuid (slurp (io/file result-tmpdir "uuid"))
          ]

      (is (= (.toString result-tmpdir) (.toString tmpdir)))
      (is (.exists out-file))
      (let [result (->  (shell/sh "zip" "-T" (.toString out-file)) :out)]
        (is (= (re-find #" OK$" result) " OK"))
        )
      )
    (fs/delete-dir tmpdir)
    )
  )
)
