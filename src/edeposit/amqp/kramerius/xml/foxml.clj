(ns edeposit.amqp.kramerius.xml.foxml
  (:require [clojure.data.xml :as xml]
            [clojure.data.zip.xml :as zx]
            [clojure.zip :as zip]
            [clojure.pprint :as pp]
            )
  )

(defn to-sexp [el]
  (if (associative? el)
    (let [{tag :tag attrs :attrs content :content} el]
      [(keyword (str "mods:" (name tag))) attrs (map to-sexp content)]
      )
    (if (seq? el) (map to-sexp el) el)
    )
  )


(defn foxml
  "transforms xml mods into oai_dc. Returns xml/root structure."
  [mods dcs {:keys [uuid label created last-modified]}]
  (let [mods-0 (first mods)
        ]
    )
  (xml/sexp-as-element
   [:foxml:digitalObject  {:PID uuid :VERSION "1.1"
                           :xsi:schemaLocation "info:fedora/fedora-system:def/foxml# http://www.fedora.info/definitions/1/0/foxml1-1.xsd" 
                           :xmlns:foxml "info:fedora/fedora-system:def/foxml#" 
                           :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"}
    [:foxml:objectProperties
     [:foxml:property {:VALUE label :NAME "info:fedora/fedora-system:def/model#label"}]
     [:foxml:property {:VALUE "Active" :NAME "info:fedora/fedora-system:def/model#state"}]
     [:foxml:property {:VALUE "fedoraAdmin" :NAME "info:fedora/fedora-system:def/model#ownerId"}]
     [:foxml:property {:VALUE (.toString created) :NAME "info:fedora/fedora-system:def/model#createdDate"}]
     [:foxml:property {:VALUE (.toString last-modified) :NAME "info:fedora/fedora-system:def/view#lastModifiedDate"}]
     ]
    (for [dc dcs]
      [:foxml:datastream {:VERSIONABLE "true" :STATE "A" :CONTROL_GROUP "X" :ID "DC"} 
       [:foxml:datastreamVersion {:FORMAT_URI "http://www.openarchives.org/OAI/2.0/oai_dc/" 
                                  :MIMETYPE "text/xml" 
                                  :CREATED (.toString created)
                                  :LABEL "Dublin Core Record for this object" 
                                  :ID "DC.1"}
        [:foxml:xmlContent dc]]]
      )
    (for [mods-one mods]
      [:foxml:datastream {:VERSIONABLE "true" :STATE "A" :CONTROL_GROUP "X" :ID "BIBLIO_MODS"} 
       [:foxml:xmlContent 
        [:mods:modsCollection {:xmlns:mods "http://www.loc.gov/mods/v3"}
         [:mods:mods {:version "3.3"}
          (map to-sexp (:content mods-one))]]]]
      )
    ]
   )
  )

(defn mods->rdf
  [root]
  )










