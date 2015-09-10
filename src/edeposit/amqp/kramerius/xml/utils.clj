(ns edeposit.amqp.kramerius.xml.utils
  (:require [clojure.data.xml :as xml]
            [clojure.data.zip.xml :as zx]
            [clojure.zip :as zip]
            [clojure.data.zip :as dz]
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

(defn has-tags? [loc]
  (some? (some :tag (-> loc zip/node :content)))
  )

(defn xml [loc & {:keys [indent-level] :or {indent-level 0}}]
  (let [indent (s/join (repeat indent-level "  "))
        tag (-> loc zip/node :tag name)
        attrs (-> loc zip/node :attrs)
        ]
    (if (-> loc has-tags? not)
      (let [content (-> loc zx/text)
            attrs-formated (s/join " " (for [[k v] attrs] (format "%s=\"%s\"" (name k) v)))
            ]
        (if (s/blank? attrs-formated)
          (if (s/blank? content)
            (format "\n%s<%s/>" indent tag)
            (format "\n%s<%s>%s</%s>" indent tag content tag)
            )
          (if (s/blank? content)
            (format "\n%s<%s %s/>" indent tag attrs-formated)
            (format "\n%s<%s %s>%s</%s>" indent tag attrs-formated content tag)
            )
          )
        )
      (let [attrs-formated (s/join " " (for [[k v] attrs] (format "%s=\"%s\"" (name k) v)))
            content (apply str (for [child (-> loc zip/down dz/right-locs)] 
                                 (xml child :indent-level (inc indent-level))))
            ]
        (if (s/blank? attrs-formated)
          (format "\n%s<%s>%s\n%s</%s>" indent tag content indent tag)
          (format "\n%s<%s %s>%s\n%s</%s>" indent tag attrs-formated content indent tag)
          )
        )
      )
    )
  )

(defn emit [root]
  (let [loc (-> root zip/xml-zip)]
    (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" (xml loc)))
  )

