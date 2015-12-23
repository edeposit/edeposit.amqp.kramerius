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

