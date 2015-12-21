(ns edeposit.amqp.kramerius.email-test
  (:require [clojure.test :refer :all]
            [edeposit.amqp.kramerius.email.utils :as u]
            [edeposit.amqp.kramerius.handlers :as h]
            [me.raynes.fs :as fs]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.java.shell :as shell]
            [me.raynes.fs :as fs]
            [postal.core :as pc]
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
      (is (re-find #"<h1>Balicek k exportu</h1>" result ))
      (is (re-find #"<p>Dobry den, posilame balicek k importu do Krameria</p>"  result))
      (is (re-find #"<th>uuid</th><td>UUID</td>" result ))
      (is (re-find #"<th>isbn</th><td>ISBN</td>" result ))
      )
    )
  )

(deftest prepare-email-from-workdir
  (testing "email from workdir"
    (let [name "export-to-kramerius-request-TEST-ID"
          workdir (io/file "/tmp" (-> name fs/temp-name))
          ]
      (fs/copy-dir (-> name io/resource io/file) workdir)
      (let [email ((h/prepare-email-from-workdir :from "edeposit@edeposit.cz" :to "stavel.jan@gmail.com")
                   workdir)]
        (is (= "edeposit@edeposit.cz" (:from email)))
        (is (= "stavel.jan@gmail.com" (:to email)))
        (is (= "eDeposit: balicek k importu do Krameria" (:subject email)))
        (let [out-dir (io/file workdir "communication-with-kramerius-administrator")
              [_ _] (h/save-email-at-workdir [workdir email])
              ]
          (.exists out-dir)
          (.exists (io/file out-dir "email-with-package.eml"))
          (let [result #spy/d 
                ((h/sendmail (fn [msg] {:code 0, :error :SUCCESS, :message "message sent"})) [workdir email])
                ;((h/sendmail pc/send-message) [workdir email])
                ]
            )
          )
        )
      )
    )
  )
