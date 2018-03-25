(ns bridge.chapter.data
  (:require [bridge.data.datomic :as datomic]
            [bridge.data.slug :as slug]
            bridge.spec
            [clojure.spec.alpha :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Queries

(defn chapter-id-by-slug [db slug]
  (datomic/entid db [:chapter/slug slug]))

(defn person-is-organiser? [db chapter-id person-id]
  (not (empty? (datomic/q '[:find ?chapter ?person :in $ ?chapter ?person :where
                            [?chapter :chapter/organisers ?person]]
                          db chapter-id person-id))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Authorisations

(defn check-chapter-organiser [db chapter-id active-user-id]
  (when-not (person-is-organiser? db chapter-id active-user-id)
    {:error :bridge/not-chapter-organiser}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Transactions

(defn new-chapter-tx [organiser-id
                      {:chapter/keys [title]
                       :as chapter}]
  (-> (s/assert :bridge/new-chapter chapter)
      (merge {:chapter/slug       (slug/->slug title)
              :chapter/status     :status/active
              :chapter/organisers [{:db/id organiser-id}]})))

(defn save-new-chapter! [conn chapter-tx]
  (datomic/transact! conn [chapter-tx]))

(comment

  (orchestra.spec.test/instrument)

  (new-chapter-tx [:person/email "test@cb.org"]
                  #:chapter{:title    "ClojureBridge Hermanus"
                            :location "Hermanus"})
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Schema

(def schema
  [{:db/ident       :chapter/title
    :db/cardinality :db.cardinality/one
    :db/valueType   :db.type/string}

   {:db/ident       :chapter/slug
    :db/cardinality :db.cardinality/one
    :db/valueType   :db.type/string
    :db/unique      :db.unique/value}

   {:db/ident       :chapter/status
    :db/cardinality :db.cardinality/one
    :db/valueType   :db.type/keyword}

   {:db/ident       :chapter/organisers
    :db/cardinality :db.cardinality/many
    :db/valueType   :db.type/ref
    :db/doc         "ref to :person"}

   {:db/ident       :chapter/location
    :db/cardinality :db.cardinality/one
    :db/valueType   :db.type/string}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Specs

(s/def :chapter/status #{:status/active})
(s/def :chapter/title :bridge.spec/required-string)
(s/def :chapter/slug :bridge.spec/slug)
(s/def :chapter/location :bridge.spec/required-string)
(s/def :chapter/organisers (s/coll-of :bridge.datomic/ref :min-count 1))

(s/def :bridge/new-chapter
  (s/keys :req [:chapter/title :chapter/location]))

(s/def :bridge/chapter
  (s/merge :bridge/new-chapter
           (s/keys :req [:chapter/status :chapter/slug :chapter/organisers]
                   :opt [:chapter/events])))

(s/def :bridge/new-chapter-tx :bridge/chapter)

(s/fdef new-chapter-tx
        :args (s/cat :active-user-id :bridge.datomic/id
                     :new-chapter :bridge/new-chapter)
        :ret :bridge/new-chapter-tx)