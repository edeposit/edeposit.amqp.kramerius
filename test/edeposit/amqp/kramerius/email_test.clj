(ns edeposit.amqp.kramerius.email-test
  (:require [clojure.test :refer :all]
            [edeposit.amqp.kramerius.email.utils :as u]
            [edeposit.amqp.kramerius.handlers :as h]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.java.shell :as shell]
            [me.raynes.fs :as fs]
            )
  )
  
(deftest email-creation
  (testing "email creation"
    (let [result (u/body-with-table
                  "Balicek k exportu"
                  "Dobry den, posilame balicek k importu do Krameria"
                  {:uuid "UUID"
                   :isbn "ISBN"}
                  )]
      (is (.contains result "<h1>Balicek k exportu</h1>"))
      (is (.contains result "<p>Dobry den, posilame balicek k importu do Krameria</p>"))
      (is (.contains result "<th>uuid</th><td>UUID</td>"))
      (is (.contains result "<th>isbn</th><td>ISBN</td>"))
      )
    )
  )

(deftest prepare-email-from-workdir
  (testing "email from workdir"
    (let [name "export-to-kramerius-request-TEST-ID"
          workdir (io/file "/tmp" (-> name fs/temp-name))
          ]
      (fs/copy-dir (-> name io/resource io/file) workdir)
      (let [email ((h/prepare-mail-from-workdir "from@edeposit.cz" "to@localhost") workdir)]
        (is (= "from@edeposit.cz" (:from email)))
        (is (= "to@localhost" (:to email)))
        (is (= "eDeposit: balicek k importu do Krameria" (:subject email)))
        )
      )
    )
  )
