(ns edeposit.amqp.kramerius.handlers-test
  (:require [clojure.test :refer :all]
            [edeposit.amqp.kramerius.handlers :as h]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            )
  )

(deftest save-request-payload-test
  (testing "save request payload"
    (def payload (slurp "resources/export-request.json"))
    (def tmpdir (h/save-request-payload nil payload))

    (is (.exists tmpdir))
    (is (.exists (io/file tmpdir "marcxml.xml")))
    (is (.exists (io/file tmpdir "marcxml.xml.b64")))
    (is (.exists (io/file tmpdir "robotandbaby.pdf")))
    (is (.exists (io/file tmpdir "robotandbaby.pdf.b64")))
    (is (.exists (io/file tmpdir "filename")))
    (is (.exists (io/file tmpdir "uuid")))

    (is (= "e65d9072-2c9b-11e5-99fd-b8763f0a3d61"
           (slurp (io/file tmpdir "uuid"))))
    (is (-> (slurp (io/file tmpdir "filename"))
            (= "resources/robotandbaby.pdf")
            ))
    (fs/delete-dir tmpdir)
    )
  )

