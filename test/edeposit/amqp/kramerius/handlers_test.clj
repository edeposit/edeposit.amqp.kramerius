(ns edeposit.amqp.kramerius.handlers-test
  (:require [clojure.test :refer :all]
            [edeposit.amqp.kramerius.handlers :as h]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
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
    (is (.exists (io/file tmpdir "oai_marc.xml")))
    (is (.exists (io/file tmpdir "oai_marc.xml.b64")))
    (is (.exists (io/file tmpdir "robotandbaby.pdf")))
    (is (.exists (io/file tmpdir "robotandbaby.pdf.b64")))
    (is (.exists (io/file tmpdir "filename")))
    (is (.exists (io/file tmpdir "uuid")))

    (is (= (fs/size (io/file tmpdir "oai_marc.xml"))
           (fs/size (io/file "resources" "oai_marc.xml"))
           ))

    (is (= (fs/size (io/file tmpdir "robotandbaby.pdf"))
           (fs/size (io/file "resources" "robotandbaby.pdf"))
           ))
    
    (is (= "e65d9072-2c9b-11e5-99fd-b8763f0a3d61"
           (slurp (io/file tmpdir "uuid"))))
    (is (-> (slurp (io/file tmpdir "filename"))
            (= "resources/robotandbaby.pdf")
            ))
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
          new-headers (-> metadata :headers (assoc "UUID" (.toString tmpdir)))
          new-metadata (-> metadata (assoc :headers new-headers))
          ]
      (def response-tmpdir (h/save-marcxml2mods-response [new-metadata payload]))
      (is (= (.toString response-tmpdir)
             (.toString tmpdir)
             ))
      (is (= (.exists (io/file response-tmpdir "marcxml2mods-response-metadata.clj"))))
      (is (= (.exists (io/file response-tmpdir "marcxml2mods-response-payload.json"))))
      (is (= (slurp (io/file response-tmpdir "marcxml2mods-response-payload.json"))
             (slurp "resources/marcxml2mods-response-payload.json")))
      )
    (fs/delete-dir tmpdir)
    )
  )
