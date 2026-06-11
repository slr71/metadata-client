(ns metadata-client.core
  (:use [kameleon.uuids :only [uuidify]]
        [medley.core :only [remove-vals]])
  (:require [cemerick.url :as curl]
            [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.tools.logging :as log]))

(defprotocol Client
  "A client library for the Metadata API."

  (find-avus
    [_ username criteria]
    "Searches for AVUs that match the given search criteria.
     Available criteria are `:attribute`, `:target-type`, `:target-id`, `:value`, and `:unit`.
     Each criterion can be either an acceptable match or a list of acceptable matches.")

  (search-avus
    [_ username criteria]
    "Searches for AVUs that match the given search criteria.
     Available criteria are `:attribute`, `:target-type`, `:target-id`, `:value`, and `:unit`.
     Each criterion must be a list of acceptable matches.")

  (delete-avus
    [_ username target-types target-ids avus]
    "Deletes AVUs matching those in the given `avus` list from the given `target-ids`.")

  (filter-by-avus
    [_ username target-types target-ids avus]
    "Filters the given target IDs by returning a list of any that have metadata with the given
     `attrs` and `values`.")

  (list-avus
    [_ username target-type target-id]
    [_ username target-type target-id opts]
    "Lists all AVUs associated with the target item.")

  (update-avus
    [_ username target-type target-id body]
    [_ username target-type target-id body opts]
    "Adds or updates Metadata AVUs on the given target item.")

  (set-avus
    [_ username target-type target-id body]
    [_ username target-type target-id body opts]
    "Sets Metadata AVUs on the given target item.
     Any AVUs not included in the request will be deleted. If the AVUs are omitted, then all AVUs for the
     given target ID will be deleted.")

  (copy-metadata-avus
    [_ username target-type target-id dest-items]
    "Copies all Metadata Template AVUs from the data item with the ID given in the URL to other data
     items sent in the request body.")

  (delete-ontology
    [_ username ontology-version]
    "Marks an Ontology as deleted in the database.")

  (list-ontologies
    [_ username]
    "List Ontology Details")

  (list-hierarchies
    [_ username ontology-version]
    [_ username ontology-version opts]
    "List Ontology Hierarchies saved for the given `ontology-version`.")

  (filter-hierarchies
    [_ username ontology-version attrs target-type target-id]
    "Filters Ontology Hierarchies saved for the given `ontology-version`,
     returning only the hierarchy's leaf-classes that are associated with the given target.")

  (filter-hierarchies-batch
    [_ username ontology-version attrs target-types target-ids]
    "Filters Ontology Hierarchies saved for the given `ontology-version` for multiple targets.
     Returns per-target results: {:targets [{:id UUID :hierarchies [...]} ...]}.")

  (filter-targets-by-ontology-search
    [_ username ontology-version attrs search-term target-types target-ids]
    "Filters the given target IDs by returning only those that have any of the given `attrs`
     and Ontology class IRIs as values whose labels match the given Ontology class `label`.")

  (filter-hierarchy
    [_ username ontology-version root-iri attr target-types target-ids]
    "Filters an Ontology Hierarchy, rooted at the given `root-iri`, returning only the
     hierarchy's leaf-classes that are associated with the given targets.")

  (filter-hierarchy-targets
    [_ username ontology-version root-iri attr target-types target-ids]
    "Filters the given target IDs by returning only those that are associated with any Ontology
     classes of the hierarchy rooted at the given `root-iri`.")

  (filter-unclassified
    [_ username ontology-version root-iri attr target-types target-ids]
    "Filters the given target IDs by returning a list of any that are not associated with any
     Ontology classes of the hierarchy rooted at the given `root-iri`."))

(defn- metadata-url
  [base-url & components]
  (log/debug "using metadata base URL" base-url)
  (str (apply curl/url base-url (map curl/url-encode components))))

(defn- get-options
  [params & {:keys [as] :or {as :stream}}]
  {:query-params     params
   :as               as
   :follow-redirects false})

(defn- post-options
  [body params & {:keys [as] :or {as :stream}}]
  {:query-params     params
   :body             body
   :content-type     :json
   :as               as
   :follow-redirects false})

(def ^:private delete-options get-options)
(def ^:private put-options post-options)

(deftype MetadataClient [base-url]
  Client

  (find-avus
    [_ username params]
    (let [params (remove-vals nil? (select-keys params [:attribute :target-type :target-id :value :unit]))]
      (:body (http/get (metadata-url base-url "avus")
                       (get-options (assoc params :user username)
                                    :as :json)))))

  (search-avus
    [_ username params]
    (let [body (remove-vals nil? (select-keys params [:attribute :target-type :target-id :value :unit]))]
      (:body (http/post (metadata-url base-url "avus" "search")
                        (post-options (json/encode body)
                                      {:user username}
                                      :as :json)))))

  (delete-avus
    [_ username target-types target-ids avus]
    (http/post (metadata-url base-url "avus" "deleter")
               (post-options (json/encode {:target-types target-types
                                           :target-ids   target-ids
                                           :avus         avus})
                             {:user username}))
    nil)

  (filter-by-avus
    [_ username target-types target-ids avus]
    (->> (http/post (metadata-url base-url "avus" "filter-targets")
                    (post-options (json/encode {:target-types target-types
                                                :target-ids   target-ids
                                                :avus         avus})
                                  {:user username}
                                  :as :json))
         :body
         :target-ids
         (map uuidify)))

  (list-avus
    [client username target-type target-id]
    (list-avus client username target-type target-id {:as :stream}))

  (list-avus
    [_ username target-type target-id {:keys [as] :or {as :stream}}]
    (http/get (metadata-url base-url "avus" target-type target-id)
              (get-options {:user username} :as as)))

  (update-avus
    [client username target-type target-id body]
    (update-avus client username target-type target-id body {:as :stream}))

  (update-avus
    [_ username target-type target-id body {:keys [as] :or {as :stream}}]
    (http/post (metadata-url base-url "avus" target-type target-id)
               (post-options body {:user username} :as as)))

  (set-avus
    [client username target-type target-id body]
    (set-avus client username target-type target-id body {:as :stream}))

  (set-avus
    [_ username target-type target-id body {:keys [as] :or {as :stream}}]
    (http/put (metadata-url base-url "avus" target-type target-id)
              (put-options body {:user username} :as as)))

  (copy-metadata-avus
    [_ username target-type target-id dest-items]
    (http/post (metadata-url base-url "avus" target-type target-id "copy")
               (post-options (json/encode {:targets dest-items}) {:user username})))

  (delete-ontology
    [_ username ontology-version]
    (http/delete (metadata-url base-url "admin" "ontologies" ontology-version)
                 (delete-options {:user username})))

  (list-ontologies
    [_ username]
    (-> (http/get (metadata-url base-url "ontologies")
                  (get-options {:user username} :as :json))
        :body))

  (list-hierarchies
    [client username ontology-version]
    (list-hierarchies client username ontology-version {}))

  (list-hierarchies
    [_ username ontology-version {:keys [as] :or {as :stream}}]
    (http/get (metadata-url base-url "ontologies" ontology-version)
              (get-options {:user username} :as as)))

  (filter-hierarchies
    [_ username ontology-version attrs target-type target-id]
    (->> (http/post (metadata-url base-url "ontologies" ontology-version "filter")
                    (post-options (json/encode {:attrs attrs :type target-type :id target-id})
                                  {:user username}
                                  :as :json))
         :body))

  (filter-hierarchies-batch
    [_ username ontology-version attrs target-types target-ids]
    (->> (http/post (metadata-url base-url "ontologies" ontology-version "filter-targets-batch")
                    (post-options (json/encode {:attrs        attrs
                                                :target-types target-types
                                                :target-ids   target-ids})
                                  {:user username}
                                  :as :json))
         :body))

  (filter-targets-by-ontology-search
    [_ username ontology-version attrs search-term target-types target-ids]
    (->> (http/post (metadata-url base-url "ontologies" ontology-version "filter-targets")
                    (post-options (json/encode {:attrs        attrs
                                                :target-types target-types
                                                :target-ids   target-ids})
                                  {:user username :label search-term}
                                  :as :json))
         :body
         :target-ids
         (map uuidify)))

  (filter-hierarchy
    [_ username ontology-version root-iri attr target-types target-ids]
    (->> (http/post (metadata-url base-url "ontologies" ontology-version root-iri "filter")
                    (post-options (json/encode {:target-types target-types :target-ids target-ids})
                                  {:user username :attr attr}
                                  :as :json))
         :body))

  (filter-hierarchy-targets
    [_ username ontology-version root-iri attr target-types target-ids]
    (->> (http/post (metadata-url base-url "ontologies" ontology-version root-iri "filter-targets")
                    (post-options (json/encode {:target-types target-types :target-ids target-ids})
                                  {:user username :attr attr}
                                  :as :json))
         :body
         :target-ids
         (map uuidify)))

  (filter-unclassified
    [_ username ontology-version root-iri attr target-types target-ids]
    (->> (http/post (metadata-url base-url "ontologies" ontology-version root-iri "filter-unclassified")
                    (post-options (json/encode {:target-types target-types :target-ids target-ids})
                                  {:user username :attr attr}
                                  :as :json))
         :body
         :target-ids
         (map uuidify))))

(defn new-metadata-client [base-url]
  (MetadataClient. base-url))
