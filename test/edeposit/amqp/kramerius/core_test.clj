(ns edeposit.amqp.kramerius.core-test
  (:require [clojure.test :refer :all]
            [edeposit.amqp.kramerius.core :as c]
            [edeposit.amqp.kramerius.handlers :as h]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]
            )
  )


(comment
 (deftest request-with-tmpdir-obs
   (testing "Observable request-with-tmpdir testing")
   (def metadata "aa")
   (def payload (slurp "resources/export-request.json"))
    
   (def request-obs (.publish (Observable/just [metadata payload])))
   (def request-with-tmpdir-obs (->> request-obs 
                                     (rx/map h/request-with-tmpdir)
                                     ))
   (.connect request-obs)
   (def result (rxb/last request-with-tmpdir-obs))
   (isa? vector result)
   (is (= (count result) 2))
   (is (= "aa" (-> result (nth 0) (nth 0))))
   )

 (deftest marcxml2mods-request-test
   (testing "marcxml2mods request testing"
     (def metadata "aa")
     (def payload (slurp "resources/export-request.json"))
    
     (def request-obs (.publish (Observable/just [metadata payload])))
     (def marcxml2mods-response-obs (Observable/empty))
     (def ssh-response-obs (Observable/empty))
    
     (def obs (c/obs->obs request-obs marcxml2mods-response-obs ssh-response-obs))
    
     (.connect request-obs)
     (def request (rxb/last (:requests-to-marcxml2mods-obs obs)))
     (let [ [headers {:keys [marc_xml uuid]}] request]
       (isa? hash request)
       (is (= (slurp "resources/oai_marc.xml") marc_xml))
       (is (= "e65d9072-2c9b-11e5-99fd-b8763f0a3d61"  uuid))
       (isa? hash headers)
       (is (.exists (io/file (:uuid headers))) "temporary directory is as uuid in request")
       )
     )
   )

 (deftest ssh-export-request-test
   (testing "export to ssh request testing"
     (def metadata "aa")
     (def payload (slurp "resources/export-request.json"))
     (def marcxml2mods-response-metadata (read-string (slurp "resources/marcxml2mods-response-metadata.clj")))
     (def marcxml2mods-response-payload (slurp "resources/marcxml2mods-response-payload.json"))
    
     (def request-obs (.publish (Observable/just [metadata payload])))
     (def marcxml2mods-response-obs (.publish (Observable/just [marcxml2mods-response-metadata 
                                                                marcxml2mods-response-payload])))
     (def ssh-response-obs (Observable/empty))
    
     (def obs (c/obs->obs request-obs marcxml2mods-response-obs ssh-response-obs))
    
     (.connect request-obs)
     (.connect marcxml2mods-response-obs)
     (def request (rxb/last (:requests-to-marcxml2mods-obs obs)))
                                        ;(def ssh-request (rxb/last (:requests-to-ssh-obs obs)))
     (let [ [{:keys [uuid]} payload] request]
       (is (.exists (io/file uuid)))
       )
     )
   )
 )
