(ns clj-http.client-test
  (:use clojure.test)
  (:require [clj-http.client :as client]))

(def base-req
  {:scheme "http"
   :server-name "localhost"
   :server-port 8080})

(deftest rountrip
  (let [resp (client/request (merge base-req {:uri "/get" :method :get}))]
    (is (= 200 (:status resp)))
    (is (= "4" (get-in resp [:headers "content-length"])))
    (is (= "get\n" (:body resp)))))


(defn is-passed [middleware req]
  (let [client (middleware identity)]
    (is (= req (client req)))))

(defn is-applied [middleware req-in req-out]
  (let [client (middleware identity)]
    (is (= req-out (client req-in)))))


(deftest redirect-on-get
  (let [client (fn [req]
                 (if (= "foo.com" (:server-name req))
                   {:status 302
                    :headers {"location" "http://bar.com/bat"}}
                   {:status 200
                    :req req}))
        r-client (client/wrap-redirects client)
        resp (r-client {:server-name "foo.com" :request-method :get})]
    (is (= 200 (:status resp)))
    (is (= :get (:request-method (:req resp))))
    (is (= "http" (:scheme (:req resp))))
    (is (= "/bat" (:uri (:req resp))))))

(deftest redirect-to-get-on-head
  (let [client (fn [req]
                 (if (= "foo.com" (:server-name req))
                   {:status 303
                    :headers {"location" "http://bar.com/bat"}}
                   {:status 200
                    :req req}))
        r-client (client/wrap-redirects client)
        resp (r-client {:server-name "foo.com" :request-method :head})]
    (is (= 200 (:status resp)))
    (is (= :get (:request-method (:req resp))))
    (is (= "http" (:scheme (:req resp))))
    (is (= "/bat" (:uri (:req resp))))))

(deftest pass-on-non-redirect
  (let [client (fn [req] {:status 200 :body (:body req)})
        r-client (client/wrap-redirects client)
        resp (r-client {:body "ok"})]
    (is (= 200 (:status resp)))
    (is (= "ok" (:body resp)))))


(deftest throw-on-exceptional
  (let [client (fn [req] {:status 500})
        e-client (client/wrap-exceptions client)]
    (is (thrown-with-msg? Exception #"500"
      (e-client {})))))

(deftest pass-on-non-exceptional
  (let [client (fn [req] {:status 200})
        e-client (client/wrap-exceptions client)
        resp (e-client {})]
    (is (= 200 (:status resp)))))


(deftest apply-on-accept
  (is-applied client/wrap-accept
    {:accept :json}
    {:headers {"Accept" "application/json"}}))

(deftest pass-on-no-accept
  (is-passed client/wrap-accept
    {:uri "/foo"}))


(deftest apply-on-accept-encoding
  (is-applied client/wrap-accept-encoding
    {:accept-encoding [:identity :gzip]}
    {:headers {"Accept-Encoding" "identity, gzip"}}))

(deftest pass-on-no-accept-encoding
  (is-passed client/wrap-accept-encoding
    {:uri "/foo"}))


(deftest apply-on-content-type
  (is-applied client/wrap-content-type
    {:content-type :json}
    {:content-type "application/json"}))

(deftest pass-on-no-content-type
  (is-passed client/wrap-content-type
    {:uri "/foo"}))


(deftest apply-on-method
  (let [m-client (client/wrap-method identity)
        echo (m-client {:key :val :method :post})]
    (is (= :val (:key echo)))
    (is (= :post (:request-method echo)))
    (is (not (:method echo)))))

(deftest pass-on-no-method
  (let [m-client (client/wrap-method identity)
        echo (m-client {:key :val})]
    (is (= :val (:key echo)))
    (is (not (:request-method echo)))))


(deftest apply-on-url
  (let [u-client (client/wrap-url identity)
        resp (u-client {:url "http://google.com:8080/foo?bar=bat"})]
    (is (= "http" (:scheme resp)))
    (is (= "google.com" (:server-name resp)))
    (is (= 8080 (:server-port resp)))
    (is (= "/foo" (:uri resp)))
    (is (= "bar=bat" (:query-string resp)))))

(deftest pass-on-no-url
  (let [u-client (client/wrap-url identity)
        resp (u-client {:uri "/foo"})]
    (is (= "/foo" (:uri resp)))))