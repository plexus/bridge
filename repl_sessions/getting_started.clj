(ns getting-started)

(require '[integrant.repl.state :refer [system]]
         '[datomic.api :as d])

(bridge.dev.repl/set-datomic-mode! :peer)

(defn conn []
  (:datomic/conn (:datomic/connection system)))

(defn db []
  (d/db (:datomic/conn (:datomic/connection system))))

(let [[person]
      (d/q '[:find [(pull ?e [*])]
             :where [?e :person/confirm-email-token]]
           (db))]

  (bridge.person.data/confirm-email! (conn)
                                     (:db/id person)
                                     (:person/confirm-email-token person)))


(bridge.person.data/person-id-by-email (d/db (conn)) "arne.brasseur@gmail.com")
