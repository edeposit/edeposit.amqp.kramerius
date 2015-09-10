(ns edeposit.amqp.kramerius.xml.foxml
  (:require [clojure.data.xml :as xml]
            [clojure.data.zip.xml :as zx]
            [clojure.zip :as zip]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]
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

(defn to-sexp-dc [el]
  (if (associative? el)
    (let [{tag :tag attrs :attrs content :content} el]
      [tag attrs (map to-sexp-dc content)]
      )
    (if (seq? el) (map to-sexp-dc el) el)
    )
  )


(defn foxml
  "transforms xml mods into oai_dc. Returns xml/root structure."
  [mods dcs full-file preview-file {:keys [uuid label created last-modified fedora-import-dir storage-dir]}]
  (xml/sexp-as-element
   [:foxml:digitalObject  {:PID (str "uuid:" uuid) :VERSION "1.1"
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
        [:foxml:xmlContent (to-sexp-dc dc)]]]
      )
    (for [mods-one mods]
      [:foxml:datastream {:VERSIONABLE "true" :STATE "A" :CONTROL_GROUP "X" :ID "BIBLIO_MODS"} 
       [:foxml:xmlContent 
        [:mods:modsCollection {:xmlns:mods "http://www.loc.gov/mods/v3"}
         [:mods:mods {:version "3.3"}
          (map to-sexp (:content mods-one))]]]])
    [:foxml:datastream {:VERSIONABLE "false" :STATE "A" :CONTROL_GROUP "E" :ID "IMG_FULL"}
     [:foxml:datastreamVersion {:MIMETYPE (-> full-file :mimetype) :CREATED created :ID "IMG_FULL.0"}
      [:foxml:contentLocation 
       {:REF (str "file:" (.toString (io/file storage-dir uuid (-> full-file :filename))))
        :TYPE "URL"}]]]

    [:foxml:datastream {:VERSIONABLE "false" :STATE "A" :CONTROL_GROUP "E" :ID "IMG_PREVIEW"}
     [:foxml:datastreamVersion {:MIMETYPE (-> preview-file :mimetype) :CREATED created :ID "IMG_PREVIEW.0"}
      [:foxml:contentLocation 
       {:REF (str "file:" (.toString (io/file storage-dir uuid (-> preview-file :filename))))
        :TYPE "URL"}]]]
    ]
   )
  )
