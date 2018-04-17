;; Copyright (c) 2012-2014 Michael S. Klishin, Alex Petrov, and the ClojureWerkz Team
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns clojurewerkz.cassaforte.client
  "Provides fundamental functions for

  * connecting to Cassandra nodes and clusters
  * configuring connections
  * tuning load balancing, retries, reconnection strategies and consistency settings
  * preparing and executing queries constructed via DSL
  * working with executing results"
  (:require [clojure.java.io                    :as io]
            [clojurewerkz.cassaforte.policies   :as cp]
            [clojurewerkz.cassaforte.conversion :as conv])
  (:import [com.datastax.driver.core Statement ResultSet ResultSetFuture Host Session Cluster
            Cluster$Builder SimpleStatement PreparedStatement BoundStatement HostDistance PoolingOptions
            JdkSSLOptions JdkSSLOptions$Builder ProtocolOptions$Compression ProtocolVersion]
           [com.google.common.util.concurrent ListenableFuture Futures FutureCallback]
           [java.net URI]
           [javax.net.ssl TrustManagerFactory KeyManagerFactory SSLContext]
           [java.security KeyStore SecureRandom]
           [com.datastax.driver.core.exceptions DriverException]))

(declare build-ssl-options select-compression)

(def ^:dynamic *async* false)

;;
;; Macros
;;

(defmacro async
  "Prepare a single statement, return prepared statement"
  [body]
  `(binding [*async* true]
     (do ~body)))

;;
;; Protocols
;;

(defprotocol DummySession
  (executeAsync [_ query]))

(deftype DummySessionImpl []
  DummySession
  (executeAsync [_ query] (throw (Exception. "Not connected"))))

(defprotocol BuildStatement
  (build-statement [query]))

(extend-protocol BuildStatement
  String
  (build-statement [query]
    (build-statement (SimpleStatement. query)))

  Statement
  (build-statement [s]
    s)

  Object
  (build-statement [s]
    (throw (RuntimeException. (str "Can't build the statement out of the" (type s))))))

(defprotocol Listenable
  (add-listener [_ runnable executor]))

(deftype AsyncResult [fut]
  clojure.lang.IDeref
  (deref [_]
    (conv/to-clj @fut))

  clojure.lang.IBlockingDeref
  (deref [_ timeout-ms default-val]
    (conv/to-clj (deref fut timeout-ms default-val)))

  Listenable
  (add-listener [_ runnable executor]
    (.addListener fut runnable executor)))

;;
;; Fns
;;

(defn ^Cluster build-cluster
  "Builds an instance of Cluster you can connect to.

   Options:
     * hosts: hosts to connect to
     * port: port, listening to incoming binary CQL connections (make sure you have `start_native_transport` set to true).
     * credentials: connection credentials in the form {:username username :password password}
     * connections-per-host: specifies core number of connections per host.
     * max-connections-per-host: maximum number of connections per host.
     * retry-policy: configures the retry policy to use for the new cluster.
     * load-balancing-policy: configures the load balancing policy to use for the new cluster.

     * consistency-level: default consistency level for all queires to be executed against this cluster
     * ssl: ssl options in the form {:keystore-path path :keystore-password password} Also accepts :cipher-suites with a Seq of cipher suite specs.
     * ssl-options: pre-built SSLOptions object (overrides :ssl)
     * kerberos: enables kerberos authentication"
  [{:keys [hosts
           port
           credentials
           connections-per-host
           max-connections-per-host
           consistency-level
           retry-policy
           reconnection-policy
           load-balancing-policy
           ssl
           ssl-options
           kerberos
           protocol-version
           compression]}]
  (let [^Cluster$Builder builder        (Cluster/builder)
        ^PoolingOptions pooling-options (PoolingOptions.)]
    (when port
      (.withPort builder port))
    (when protocol-version
      (.withProtocolVersion builder (ProtocolVersion/fromInt protocol-version)))
    (when credentials
      (.withCredentials builder (:username credentials) (:password credentials)))
    (when connections-per-host
      (.setCoreConnectionsPerHost pooling-options HostDistance/LOCAL
                                  connections-per-host))
    (when max-connections-per-host
      (.setMaxConnectionsPerHost pooling-options HostDistance/LOCAL
                                 max-connections-per-host))
    (.withPoolingOptions builder pooling-options)
    (doseq [h hosts]
      (.addContactPoint builder h))
    (when retry-policy
      (.withRetryPolicy builder retry-policy))
    (when reconnection-policy
      (.withReconnectionPolicy builder reconnection-policy))
    (when load-balancing-policy
      (.withLoadBalancingPolicy builder load-balancing-policy))
    (when compression
      (.withCompression builder (select-compression compression)))
    (when ssl
      (.withSSL builder (build-ssl-options ssl)))
    (when ssl-options
      (.withSSL builder ssl-options))
    (.build builder)))

(defn- ^JdkSSLOptions build-ssl-options
  [{:keys [keystore-path keystore-password cipher-suites]}]
  (let [keystore-stream   (io/input-stream keystore-path)
        keystore          (KeyStore/getInstance "JKS")
        ssl-context       (SSLContext/getInstance "SSL")
        keymanager        (KeyManagerFactory/getInstance (KeyManagerFactory/getDefaultAlgorithm))
        trustmanager      (TrustManagerFactory/getInstance (TrustManagerFactory/getDefaultAlgorithm))
        password          (char-array keystore-password)
        ssl-cipher-suites (when cipher-suites (into-array String cipher-suites))]
    (.load keystore keystore-stream password)
    (.init keymanager keystore password)
    (.init trustmanager keystore)
    (.init ssl-context (.getKeyManagers keymanager) (.getTrustManagers trustmanager) nil)
    (if ssl-cipher-suites
      (.. (JdkSSLOptions$Builder.) (withSSLContext ssl-context) (withCipherSuites ssl-cipher-suites) (build))
      (.. (JdkSSLOptions$Builder.) (withSSLContext ssl-context) (build)))))

(defn- ^ProtocolOptions$Compression select-compression
  [compression]
  (case compression
    :snappy ProtocolOptions$Compression/SNAPPY
    :lz4 ProtocolOptions$Compression/LZ4
    ProtocolOptions$Compression/NONE))

(defn- connect-or-close
  "Attempts to connect to the cluster or closes the cluster and reraises any errors."
  [^Cluster cluster & [keyspace]]
  (try
    (if keyspace
      (.connect cluster keyspace)
      (.connect cluster))
    (catch DriverException e
      (.close cluster)
      (throw e))))

(defn ^Session connect
  "Connects to the Cassandra cluster. Use `build-cluster` to build a cluster."
  ([hosts]
   (connect-or-close (build-cluster {:hosts hosts})))
  ([hosts keyspace-or-opts]
   (if (string? keyspace-or-opts)
     (connect hosts keyspace-or-opts {})
     (let [keyspace (:keyspace keyspace-or-opts)
           opts     (dissoc keyspace-or-opts :keyspace)]
       (if keyspace
         (connect hosts keyspace opts)
         (connect-or-close (-> opts (merge {:hosts hosts}) build-cluster))))))
  ([hosts keyspace opts]
   (let [c (build-cluster (merge opts {:hosts hosts}))]
     (connect-or-close c (name keyspace)))))

(defn ^Session connect-with-uri
  ([^String uri]
   (connect-with-uri uri {}))
  ([^String uri opts]
   (let [^URI u (URI. uri)]
     (connect [(.getHost u)] (merge {:port (.getPort u) :keyspace (-> u .getPath (.substring 1))} opts)))))

(defn disconnect
  "1-arity version receives Session, and shuts it down. It doesn't shut down all other sessions
   on same cluster."
  [^Session session]
  (.close session))

(defn disconnect!
  "Shuts the cluster and session down.  If you have other sessions, use the safe `disconnect` function instead."
  [^Session session]
  (.close (.getCluster session)))

(defn shutdown-cluster
  "Shuts down provided cluster"
  [^Cluster cluster]
  (.close cluster))

(defn bind
  "Binds prepared statement to values, for example:

  With string statement:

    (let [r        {:name \"Alex\" :city \"Munich\" :age (int 19)}
          prepared (client/prepare session
                                   (insert :users
                                           {:name ?
                                            :city ?
                                            :age  ?}))]
      (client/execute session
                      (client/bind prepared
                                   {:name \"Alex\" :city \"Munich\" :age (int 19)})))
  "
  [^PreparedStatement statement values]
  ;; TODO: matching
  (if (map? values)
    (.bind statement (to-array (vals values)))
    (.bind statement (to-array values))))

(defn prepare
  [^Session session  statement]
  (.prepare session statement))

;; (defn execute-async)

(defn execute
  "Executes a statement"
  ([^Session session query]
   (let [^Statement built-statement (build-statement query)]
     ;; (println built-statement)
     (if *async*
       (AsyncResult. (.executeAsync session built-statement))
       (-> (.execute session built-statement)
           (conv/to-clj)))))
  ([^Session session query & {:keys [retry-policy
                                     consistency-level
                                     fetch-size
                                     enable-tracing
                                     default-timestamp]}]
   (let [^Statement built-statement (build-statement query)]
     (when default-timestamp
       (.setDefaultTimestamp built-statement default-timestamp))
     (when enable-tracing
       (.enableTracing built-statement))
     (when fetch-size
       (.setFetchSize built-statement (int fetch-size)))
     (when retry-policy
       (.setRetryPolicy built-statement retry-policy))
     (when consistency-level
       (.setConsistencyLevel built-statement consistency-level))
     (if *async*
       (AsyncResult. (.executeAsync session built-statement))
       (-> (.execute session built-statement)
           (conv/to-clj))))))

(defn ^String export-schema
  "Exports the schema as a string"
  [^Session client]
  (-> client
      .getCluster
      .getMetadata
      .exportSchemaAsString))

(defn get-hosts
  "Returns all nodes in the cluster"
  [^Session session]
  (map (fn [^Host host]
         {:datacenter (.getDatacenter host)
          :address    (.getHostAddress (.getAddress host))
          :rack       (.getRack host)
          :is-up      (.isUp host)})
       (-> session
           .getCluster
           .getMetadata
           .getAllHosts)))

;; defn get-replicas
;; defn get-cluster-name
;; defn get-keyspace
;; defn get-keyspaces
;; defn rebuild-schema
