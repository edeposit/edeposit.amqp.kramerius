(ns edeposit.amqp.kramerius.handlers-test
  (:require [clojure.test :refer :all]
            [edeposit.amqp.kramerius.handlers :as h]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.data.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]
            )
  )

(deftest with-tmpdir-test
  (testing "add tmpdir to request"
    (def payload (slurp "resources/export-request.json"))
    (def result (h/request-with-tmpdir  [nil payload]))

    (isa? vector result)
    (let [ [[metadata payload] tmpdir] result]
      (is (.exists tmpdir))
      (is (= metadata nil))
      (is (= payload (slurp "resources/export-request.json")))
      (fs/delete-dir tmpdir)
      )
    )
  )

(deftest save-request-test
  (testing "save request payload"
    (def payload (slurp "resources/export-request.json"))
    (def tmpdir (fs/temp-dir "test-export-to-kramerius-request-"))
    (def result (h/save-request  [[nil payload] tmpdir]))

    (is (.exists tmpdir))
    (is (.exists (io/file tmpdir "uuid")))
    (is (.exists (io/file tmpdir "urnnbn")))
    (is (.exists (io/file tmpdir "oai_marc.xml")))
    (is (.exists (io/file tmpdir "oai_marc.xml.b64")))

    (is (.exists (io/file tmpdir "img_full")))
    (is (.exists (io/file tmpdir "img_full" "robotandbaby.pdf")))
    (is (.exists (io/file tmpdir "img_full" "mimetype")))
    (is (.exists (io/file tmpdir "img_full" "filename")))

    (is (.exists (io/file tmpdir "img_preview")))
    (is (.exists (io/file tmpdir "img_preview" "robotandbaby_001.jpg")))
    (is (.exists (io/file tmpdir "img_preview" "mimetype")))
    (is (.exists (io/file tmpdir "img_preview" "filename")))
    
    (is (= (fs/size (io/file tmpdir "oai_marc.xml"))
           (fs/size (io/file "resources" "oai_marc.xml"))))

    (is (= (fs/size (io/file tmpdir "img_full" "robotandbaby.pdf"))
           (fs/size (io/file "resources" "robotandbaby.pdf"))))

    (is (= (fs/size (io/file tmpdir "img_preview" "robotandbaby_001.jpg"))
           (fs/size (io/file "resources" "robotandbaby_001.jpg"))))
    
    (is (= "e65d9072-2c9b-11e5-99fd-b8763f0a3d61" (slurp (io/file tmpdir "uuid"))))
    (is (= "urn:nbn:cz:mzk-0005ol" (slurp (io/file tmpdir "urnnbn"))))

    (is (= "robotandbaby.pdf" (slurp (io/file tmpdir "img_full" "filename"))))
    (is (= "application/pdf" (slurp (io/file tmpdir "img_full" "mimetype"))))
    (is (= "robotandbaby_001.jpg" (slurp (io/file tmpdir "img_preview" "filename"))))
    (is (= "image/jpeg" (slurp (io/file tmpdir "img_preview" "mimetype"))))

    (fs/delete-dir tmpdir)
    )
  )

(deftest save-marcxml2mods-response-test
  (testing "save marcxml2mods response"
    (def payload (slurp "resources/export-request.json"))
    (def tmpdir (fs/temp-dir "test-export-to-kramerius-request-"))

    (h/save-request  [[nil payload] tmpdir])

    (let [ metadata (read-string (slurp "resources/marcxml2mods-response-metadata.clj"))
          payload (slurp "resources/marcxml2mods-response-payload.json")
          actual-metadata (-> metadata (assoc :headers 
                                              (-> metadata :headers (assoc "UUID" (.toString tmpdir)))))
          [mods_files response-tmpdir] (h/save-marcxml2mods-response [actual-metadata payload])
          ]
      (is (= (.toString response-tmpdir)
             (.toString tmpdir)
             ))
      (is (= (.exists (io/file response-tmpdir "marcxml2mods-response-metadata.clj"))))
      (is (= (.exists (io/file response-tmpdir "marcxml2mods-response-payload.json"))))
      (is (= (slurp (io/file response-tmpdir "marcxml2mods-response-payload.json"))
             (slurp "resources/marcxml2mods-response-payload.json")))

      (isa? vector mods_files)
      (is (= (count mods_files) 1))
      
      (doseq [mods mods_files]
        (is (.contains mods "<?xml version="))
        (is (.contains mods "<mods:mods version="))
        (is (.contains mods "Umění programování v UNIXu"))
        )
      )

    (fs/delete-dir tmpdir)
    )
  )

(deftest parse-mods-test
  (testing "parse mods files"
    (def payload (slurp "resources/export-request.json"))
    (def tmpdir (fs/temp-dir "test-export-to-kramerius-request-"))

    (h/save-request  [[nil payload] tmpdir])

    (let [ metadata (read-string (slurp "resources/marcxml2mods-response-metadata.clj"))
          payload (slurp "resources/marcxml2mods-response-payload.json")
          actual-metadata (-> metadata (assoc :headers 
                                              (-> metadata :headers (assoc "UUID" (.toString tmpdir)))))
          
          [mods response-tmpdir] (-> (h/save-marcxml2mods-response [actual-metadata payload])
                                     h/parse-mods-files
                                     )
          ]

      (is (= (.toString response-tmpdir)
             (.toString tmpdir)
             ))

      (isa? vector mods)
      (is (= (count mods) 1))
      (doseq [root (map zip/xml-zip mods)]
        (is (=  (zx/xml1-> root :titleInfo :title zx/text)
                "Umění programování v UNIXu"))
        (is (=  (zx/xml1-> root :name :namePart zx/text) 
                "Raymond, Eric S."))))

    (fs/delete-dir tmpdir)
    )
  )

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

(deftest foxml
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
      (let [root (-> (io/file result-tmpdir uuid (str uuid ".xml"))
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

      ;(pp/pprint result-tmpdir)
      ;(println (slurp (io/file result-tmpdir uuid (str uuid ".xml"))))
      )

    ;(fs/delete-dir tmpdir)
    )
  )










