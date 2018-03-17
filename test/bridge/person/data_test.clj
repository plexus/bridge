(ns bridge.person.data-test
  (:require [bridge.data.datomic :as datomic]
            [bridge.person.data :as person.data]
            [bridge.test.util :refer [conn test-setup with-database]]
            [clojure.spec.alpha :as s]
            [clojure.test :refer [deftest is use-fixtures]])
  (:import clojure.lang.ExceptionInfo))

(def db-name (str *ns*))

(use-fixtures :once test-setup)
(use-fixtures :each (with-database db-name person.data/schema))

(defn db-after-tx [tx]
  (:db-after (datomic/with (conn db-name) tx)))

(deftest new-person-tx
  (is (thrown-with-msg? ExceptionInfo #"did not conform to spec"
                        (person.data/new-person-tx {})))

  (let [TEST-EMAIL    "test@cb.org"
        TEST-PASSWORD "secret"
        tx            (person.data/new-person-tx #:person{:name     "Test Person"
                                                          :email    TEST-EMAIL
                                                          :password TEST-PASSWORD})]

    (is (s/valid? :bridge/new-person-tx tx))
    (is (= :status/active (:person/status tx)))

    (let [db       (db-after-tx [tx])
          person   (datomic/entid db [:person/email TEST-EMAIL])
          password (datomic/attr db [:person/email TEST-EMAIL] :person/password)]

      (is (some? person))
      (is (person.data/correct-password? password TEST-PASSWORD))
      (is (not (person.data/correct-password? password "wrong")))

      )))