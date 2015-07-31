(ns edeposit.amqp.kramerius.xml.mods
  (:require [clojure.data.xml :as xml]
            [clojure.data.zip.xml :as zx]
            [clojure.zip :as zip]
            )
  )

(defn with-urnnbn-identifier
  "add add <mods:identifier type='urnnbn'>cnb001492461</mods:identifier>"
  [root urnnbn]
  (assoc-in root [:content] (into (-> root :content) [(xml/element :identifier {:type "urnnbn"} urnnbn )])))

(defn mods->oai_dc
  "transforms xml mods into oai_dc. Returns xml/root structure."
  [root]
  (let [ root-> (partial zx/xml-> (zip/xml-zip root))]
    [:oai_dc:dc {:xmlns:oai_dc "http://www.openarchives.org/OAI/2.0/oai_dc/"}
     (for [title (root-> :titleInfo :title zx/text)]
       [:dc:title {:xmlns:dc "http://purl.org/dc/elements/1.1/"} title])
     (for [author   (concat(root-> :name [:role :roleTerm (zx/text= "author")] :namePart zx/text)
                           (root-> :name [:role :roleTerm (zx/text= "aut")] :namePart zx/text))]
       [:dc:creator {:xmlns:dc "http://purl.org/dc/elements/1.1/"} author])
     (for [subject (concat (root-> :subject :topic zx/text)
                           (root-> :classification zx/text))]
       [:dc:subject {:xmlns:dc "http://purl.org/dc/elements/1.1/"} subject])
     (for [description (concat (root-> :note zx/text)
                               (root-> :abstract zx/text)
                               (root-> :tableOfContents zx/text))]
        [:dc:description {:xmlns:dc "http://purl.org/dc/elements/1.1/"} description])
     (for [publisher  (root-> :originInfo :publisher zx/text)]
       [:dc:publisher {:xmlns:dc "http://purl.org/dc/elements/1.1/"} publisher])
     (for [contributor (concat(root-> :name [:role :roleTerm (zx/text= "contributor")] :namePart zx/text)
                              (root-> :name [:role :roleTerm (zx/text= "ctb")] :namePart zx/text))]
       [:dc:contributor {:xmlns:dc "http://purl.org/dc/elements/1.1/"} contributor])
     (for [date (concat (root-> :originInfo :dateIssued zx/text)
                        (root-> :originInfo :dateCreated zx/text)
                        (root-> :originInfo :dateCaptured zx/text)
                        (root-> :originInfo :dateOther zx/text))]
       [:dc:date {:xmlns:dc "http://purl.org/dc/elements/1.1/"} date])
     (for [type (concat (root-> :typeOfResource zx/text)
                        (root-> :genre [(zx/attr= :authority "dct")] zx/text))]
       [:dc:type {:xmlns:dc "http://purl.org/dc/elements/1.1/"} type])
     (for [format (concat (root-> :physicalDescription :form zx/text)
                          (root-> :physicalDescription :extent zx/text)
                           (root-> :physicalDescription :internetMediaType zx/text))]
       [:dc:format {:xmlns:dc "http://purl.org/dc/elements/1.1/"} format])
     (for [identifier (root-> :identifier)]
       [:dc:identifier {:xmlns:dc "http://purl.org/dc/elements/1.1/"} 
        (str (zx/xml1-> identifier (zx/attr :type)) ":" (zx/xml1-> identifier zx/text))])
     (for [identifier (root-> :location :url zx/text)]
       [:dc:identifier {:xmlns:dc "http://purl.org/dc/elements/1.1/"} identifier])
     (for [source (concat (root-> :relatedItem [(zx/attr= :type "original")] :titleInfo :title zx/text)
                          (root-> :relatedItem [(zx/attr= :type "original")] :location :url zx/text))]
       [:dc:source {:xmlns:dc "http://purl.org/dc/elements/1.1/"} source])
     (for [language (root-> :language :languageTerm zx/text)]
       [:dc:language {:xmlns:dc "http://purl.org/dc/elements/1.1/"} language])
     (for [relation (concat (root-> :relatedItem :titleInfo :title zx/text)
                            (root-> :relatedItem :location :url zx/text))]
       [:dc:relation {:xmlns:dc "http://purl.org/dc/elements/1.1/"} relation])
     (for [coverage (concat (root-> :subject :temporal zx/text)
                            (root-> :subject :geographic zx/text)
                            (root-> :subject :hyerarchicalGeographic zx/text)
                            (root-> :subject :cartographics :coordinates zx/text))]
       [:dc:coverage {:xmlns:dc "http://purl.org/dc/elements/1.1/"} coverage])
     ]
    )
  )

