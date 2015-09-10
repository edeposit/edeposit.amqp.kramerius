(ns edeposit.amqp.kramerius.xml.mods
  (:require [clojure.data.xml :as xml]
            [clojure.data.zip.xml :as zx]
            [clojure.zip :as zip]
            )
  )

(defn with-urnnbn-identifier
  "add add <mods:identifier type='urnnbn'>cnb001492461</mods:identifier>"
  [root urnnbn]
  (assoc-in root [:content] 
            (into (-> root :content) [(xml/element :identifier {:type "urnnbn"} urnnbn )])))

(defn mods->oai_dc
  "transforms xml mods into oai_dc. Returns xml/root structure."
  [root]
  (let [ loc (-> root zip/xml-zip) ]
    (xml/sexp-as-element
     [:oai_dc:dc {:xmlns:oai_dc "http://www.openarchives.org/OAI/2.0/oai_dc/"
                  :xmlns:dc "http://purl.org/dc/elements/1.1/"}
      (for [title (zx/xml-> loc :titleInfo :title zx/text)]
        [:dc:title {} title])
      (for [author (concat(zx/xml-> loc :name [:role :roleTerm (zx/text= "author")] 
                                    :namePart zx/text)
                          (zx/xml-> loc :name [:role :roleTerm (zx/text= "aut")] 
                                    :namePart zx/text))]
        [:dc:creator {} author])
      (for [subject (concat (zx/xml-> loc :subject :topic zx/text)
                            (zx/xml-> loc :classification zx/text))]
        [:dc:subject {} subject])
      (for [description (concat (zx/xml-> loc :note zx/text)
                                (zx/xml-> loc :abstract zx/text)
                                (zx/xml-> loc :tableOfContents zx/text))]
        [:dc:description {} description])
      (for [publisher  (zx/xml-> loc :originInfo :publisher zx/text)]
        [:dc:publisher {} publisher])
      (for [contributor (concat(zx/xml-> loc 
                                         :name 
                                         [:role :roleTerm (zx/text= "contributor")] 
                                         :namePart zx/text)
                               (zx/xml-> loc :name [:role :roleTerm (zx/text= "ctb")] 
                                         :namePart zx/text))]
        [:dc:contributor {} contributor])
      (for [date (concat (zx/xml-> loc :originInfo :dateIssued zx/text)
                         (zx/xml-> loc :originInfo :dateCreated zx/text)
                         (zx/xml-> loc :originInfo :dateCaptured zx/text)
                         (zx/xml-> loc :originInfo :dateOther zx/text))]
        [:dc:date {} date])
      (for [type (concat (zx/xml-> loc :typeOfResource zx/text)
                         (zx/xml-> loc :genre [(zx/attr= :authority "dct")] zx/text))]
        [:dc:type {} type])
      (for [format (concat (zx/xml-> loc :physicalDescription :form zx/text)
                           (zx/xml-> loc :physicalDescription :extent zx/text)
                           (zx/xml-> loc :physicalDescription :internetMediaType zx/text))]
        [:dc:format {} format])
      (for [identifier (zx/xml-> loc :identifier)]
        [:dc:identifier {} 
         (str (zx/xml1-> identifier (zx/attr :type)) ":" (zx/xml1-> identifier zx/text))])
      (for [identifier (zx/xml-> loc :location :url zx/text)]
        [:dc:identifier {} identifier])
      (for [source (concat (zx/xml-> loc :relatedItem [(zx/attr= :type "original")] 
                                     :titleInfo :title zx/text)
                           (zx/xml-> loc :relatedItem [(zx/attr= :type "original")] 
                                     :location :url zx/text))]
        [:dc:source {} source])
      (for [language (zx/xml-> loc :language :languageTerm zx/text)]
        [:dc:language {} language])
      (for [relation (concat (zx/xml-> loc :relatedItem :titleInfo :title zx/text)
                             (zx/xml-> loc :relatedItem :location :url zx/text))]
        [:dc:relation {} relation])
      (for [coverage (concat (zx/xml-> loc :subject :temporal zx/text)
                             (zx/xml-> loc :subject :geographic zx/text)
                             (zx/xml-> loc :subject :hyerarchicalGeographic zx/text)
                             (zx/xml-> loc :subject :cartographics :coordinates zx/text))]
        [:dc:coverage {} coverage])
      ]
     )
    )
  )

