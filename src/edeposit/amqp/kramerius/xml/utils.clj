(ns edeposit.amqp.kramerius.xml.utils
  (:require [clojure.data.xml :as xml]
            [clojure.data.zip.xml :as zx]
            [clojure.zip :as zip]
            [clojure.string :as s]
            [clojure.pprint :as pp]
            )
  )

(defn add-xmlns [root xmlns]
  (cond
    (= (type root) clojure.data.xml.Element)
    (-> root
        (update-in [:tag] (fn [tag] (-> tag name (->> (format "%s:%s" (name xmlns))) keyword)))
        (update-in [:content] (fn [content] (add-xmlns content xmlns))))

    (sequential? root)
    (map (fn [item] (add-xmlns item xmlns)) root)

    :else
    root
    )
  )

(defn emit [x & {:keys [indent-level out-stream] :or {indent-level 0, out-stream *out*}}]
  (let [indent (s/join (repeat indent-level "   "))]
    (cond
      (= (type x) clojure.data.xml.Element) 
      (do
        (.write out-stream (format "\n%s<%s %s>" 
                                   indent
                                   (name (:tag x))
                                   (s/join " " (for [[k v] (:attrs x)] (format "%s=\"%s\"" (name k) v)))))
        (emit (:content x) :indent-level (inc indent-level) :out-stream out-stream)
        (.write out-stream (format "\n%s</%s>" indent (name (:tag x)))))
      
      (sequential? x)
      (doseq [el x]  (emit el :indent-level indent-level :out-stream out-stream))
      
      :else
      (do
        (.write out-stream "\n")
        (.write out-stream indent)
        (doall (.write out-stream x))
        )
      )
    )
  )

