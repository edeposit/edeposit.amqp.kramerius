(ns edeposit.amqp.kramerius.core-test
  (:require [clojure.core.async :as async :refer [go go-loop <! >!]]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.set :as set]
            [clojure.test :refer :all]
            [clojure.test :refer (deftest is are testing)]
            [edeposit.amqp.kramerius.core :as c]
            [edeposit.amqp.kramerius.handlers :as h]
            [jamesmacaulay.async-tools.test :refer (deftest-async)]
            [jamesmacaulay.zelkova.signal :as z])
  )

;; (deftest-async requests
;;   (go
;;     (let [payload (slurp "resources/export-request.json")
;;           metadata :no-metadata
;;           requests (z/write-port [:no-meta :no-payload])]
;;       (let [{:keys [saved marcxml2mods-requests preview-pages]} (c/->marcxml2mods requests)
;;             channel (z/to-chan marcxml2mods-requests)
;;             saved-channel (z/to-chan saved)
;;             preview-pages-channel (z/to-chan preview-pages)
;;             aa (z/map (partial println "saved signal") saved)
;;             bb (z/map (partial println "marcxml2mods requests") marcxml2mods-requests)
;;             cc (z/map (partial println "preview pages") preview-pages)
;;             ]
;;         (async/onto-chan requests [[metadata payload]])
;;         (let [results (<! (async/into [] channel))
;;               [metadata payload] (nth results 0)]
;;           (is (contains? metadata :uuid))
;;           (is (re-find #"^/tmp/export-to-kramerius-request-" (:uuid metadata)))
;;           (is (contains? payload :marc_xml))
;;           (is (contains? payload :uuid))
;;           (is (re-find #"<\?xml version = \"1.0\" encoding = \"UTF-8\"\?>" (:marc_xml payload)))
;;           (let [preview-pages-results (<! (async/into [] preview-pages-channel))
;;                 saved-results (<! (async/into [] saved-channel))
;;                 ]
;;             ;(is (= (->> saved-results (map #(.toString %)) (apply vector)) [(:uuid metadata)]))
;;             ;(is (= (->> preview-pages-results (map #(.toString %)) (apply vector)) [(:uuid metadata)]))
;;             )
;;           )
;;         (let [marcxml2mods-responses (z/write-port [:no-meta :no-payload])
;;               {:keys [mods oai_dcs]} (c/->storage
;;                                       marcxml2mods-responses
;;                                       preview-pages)
;;               mods-channel (z/to-chan mods)
;;               oai_dcs-channel (z/to-chan oai_dcs)]
;;           )
;;         )
;;       )
;;     )
;;   )

;; (deftest-async once-call
;;   (go
;;     (let [rand (fn [input] (rand-int 100))
;;           identity (fn [input] input)]
;;       (let [requests (z/write-port [:no-input])
;;             aa (z/map rand requests)
;;             aa-source (z/input :no-input :numbers (async/mult (z/to-chan aa)))
;;             bb (z/map identity aa-source)
;;             cc (z/map identity aa-source)
;;             bb-outs (z/to-chan bb)
;;             cc-outs (z/to-chan cc)
;;             ]
;;         (async/onto-chan requests [1 2 3])
;;         (let [bb-results (<! (async/into [] bb-outs))
;;               cc-results (<! (async/into [] cc-outs))
;;               ]
;;           (is (= bb-results cc-results))
;;           )
;;         )
;;       )
;;     )
;;   )
;; (defn accum-keys
;;   ([] {:out :no-out :accum {}})
;;   ([{:keys [out accum]} item]
;;    (let [[key value] (-> item seq first)
;;          new-accum (update-in accum [key] conj value)
;;          values (-> new-accum (get key))]
;;      {:out (if (set/subset? #{0 1 2}  (set values)) key :no-out) :accum new-accum})))

;; (deftest-async group-by-test
;;   (go
;;     (let [requests (z/write-port [{:a 1}])
;;           outs (->> requests
;;                     (z/reductions accum-keys)
;;                     (z/map (fn [arg] (:out arg)))
;;                     (z/drop-if (partial = :no-out))
;;                     )
;;           outs-chan (z/to-chan outs)]
;;       (async/onto-chan requests [{:b 1} {:a 0} {:b 1} {:b 0} {:b 2} {:a 2} {:a 1}])
;;       (let [results (<! (async/into [] outs-chan))]
;;         (is (set/subset? #{1 2} #{1 2 3}))
;;         (is (= [:b :a] results))
;;         )
;;       )
;;     )
;;   )

;; (comment
;;  (deftest request-with-tmpdir-obs
;;    (testing "Observable request-with-tmpdir testing")
;;    (def metadata "aa")
;;    (def payload (slurp "resources/export-request.json"))
    
;;    (def request-obs (.publish (Observable/just [metadata payload])))
;;    (def request-with-tmpdir-obs (->> request-obs 
;;                                      (rx/map h/request-with-tmpdir)
;;                                      ))
;;    (.connect request-obs)
;;    (def result (rxb/last request-with-tmpdir-obs))
;;    (isa? vector result)
;;    (is (= (count result) 2))
;;    (is (= "aa" (-> result (nth 0) (nth 0))))
;;    )

;;  (deftest marcxml2mods-request-test
;;    (testing "marcxml2mods request testing"
;;      (def metadata "aa")
;;      (def payload (slurp "resources/export-request.json"))
    
;;      (def request-obs (.publish (Observable/just [metadata payload])))
;;      (def marcxml2mods-response-obs (Observable/empty))
;;      (def ssh-response-obs (Observable/empty))
    
;;      (def obs (c/obs->obs request-obs marcxml2mods-response-obs ssh-response-obs))
    
;;      (.connect request-obs)
;;      (def request (rxb/last (:requests-to-marcxml2mods-obs obs)))
;;      (let [ [headers {:keys [marc_xml uuid]}] request]
;;        (isa? hash request)
;;        (is (= (slurp "resources/oai_marc.xml") marc_xml))
;;        (is (= "e65d9072-2c9b-11e5-99fd-b8763f0a3d61"  uuid))
;;        (isa? hash headers)
;;        (is (.exists (io/file (:uuid headers))) "temporary directory is as uuid in request")
;;        )
;;      )
;;    )

;;  (deftest ssh-export-request-test
;;    (testing "export to ssh request testing"
;;      (def metadata "aa")
;;      (def payload (slurp "resources/export-request.json"))
;;      (def marcxml2mods-response-metadata (read-string (slurp "resources/marcxml2mods-response-metadata.clj")))
;;      (def marcxml2mods-response-payload (slurp "resources/marcxml2mods-response-payload.json"))
    
;;      (def request-obs (.publish (Observable/just [metadata payload])))
;;      (def marcxml2mods-response-obs (.publish (Observable/just [marcxml2mods-response-metadata 
;;                                                                 marcxml2mods-response-payload])))
;;      (def ssh-response-obs (Observable/empty))
    
;;      (def obs (c/obs->obs request-obs marcxml2mods-response-obs ssh-response-obs))
    
;;      (.connect request-obs)
;;      (.connect marcxml2mods-response-obs)
;;      (def request (rxb/last (:requests-to-marcxml2mods-obs obs)))
;;                                         ;(def ssh-request (rxb/last (:requests-to-ssh-obs obs)))
;;      (let [ [{:keys [uuid]} payload] request]
;;        (is (.exists (io/file uuid)))
;;        )
;;      )
;;    )
;;  )
