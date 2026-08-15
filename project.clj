(defproject org.cyverse/metadata-client "3.2.2-SNAPSHOT"
  :description "Client for the metadata service"
  :url "https://github.com/cyverse-de/metadata-client"
  :license {:name "BSD"
            :url "https://cyverse.org/license"}
  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]]
  :plugins [[jonase/eastwood "1.4.3"]
            [lein-ancient "1.0.0"]
            [test2junit "1.4.4"]]
  :dependencies [[org.clojure/clojure "1.12.5"]
                 [clj-http "3.13.0"]
                 [com.cemerick/url "0.1.1" :exclusions [com.cemerick/clojurescript.test]]
                 [cheshire "5.13.0"]
                 [medley "1.4.0"]
                 [org.cyverse/kameleon "3.0.10"]])
