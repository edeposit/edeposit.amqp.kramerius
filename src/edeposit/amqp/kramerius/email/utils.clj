(ns edeposit.amqp.kramerius.email.utils
  (:require [hiccup.core :as h]
            [clojure.string :as s]
            )
  )

(defn body-with-table
  [title text table-data]
  (let [one-item-format (fn [[k v]]
                          [:tr {}
                           [:th {} (name k)]
                           [:td {} (str v)]
                           ]
                          )
        ]
    (str (h/html [:html
                  [:body
                   [:h1 title] [:p {} text]
                   [:table
                    [:tbody
                     (map one-item-format (seq table-data))
                     ]
                    ]
                   ]
                  ]
                 )
         )
    )
  )
