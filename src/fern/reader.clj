(ns fern.reader
  (:refer-clojure :exclude [read-string])
  (:require [clojure.string :as str]
            [fern.either :as e]
            [fern.map :refer [map-keys map-vals]]
            [fern.keyword :refer [renamespace]]
            [instaparse.core :as insta]
            [instaparse.gll :as gll]
            [instaparse.failure :as insta-failure]))

;; ========================================
;; Error reporting is important to us. We want to produce nice
;; messages with precise locations. We're going to achieve this
;; through use of Either. A "left" value is an error.

;; The most common "left" value is a marker. The second most common is
;; a collection of markers. A marker communicates where there are
;; problems in the input.

(declare source-text)

(defn marker? [v]
  (and (map? v) (contains? v :marker-text)))

(defn- pprint-failure
  [{:keys [line column text]}]
  (str/join \newline
            [(str "Parse error at line " line ", column " column ":")
             text
             (insta-failure/marker column)]))

(defn- insta-failure->marker
  [f]
  (assoc (map-keys renamespace f)
         :marker-text  (pprint-failure f)
         :source-text  (:text f)))

(defn node->marker
  [node marker-text]
  (let [m (meta node)]
    (assoc (map-keys renamespace m)
           :marker-text marker-text
           :source-text (source-text node))))

(defn exception->marker
  [node exception]
  (node->marker node (.getMessage exception)))

;; ========================================
;; Sometimes we want to display the original source text that relates
;; to a problem. Binding the whole input to a dynamic var lets us get
;; back to that after parsing has broken it down to pieces.

(def ^:private ^:dynamic *input* nil)

(defn- source-text
  [node]
  (when *input*
    (when-let [[start end] (insta/span node)]
      (subs *input* (inc start) end))))

;; ========================================
;; One level of parser eliminates comments and whitespace

(def ^:private whitespace-or-comments
  (insta/parser
   "ws-or-comments = #'\\s+' | comments
    comments = comment+
    comment = ';' #'.*?(\\r\\n|\\n|\\z)'"
   :auto-whitespace :standard))

;; ========================================
;; The next level of parser digests the input according to Clojure's
;; syntax rules for maps, lists, and vectors. Keywords and symbols are
;; recognized but not resolved at this stage.

(def expr-parser
  (insta/parser
   "<expr> =  (list-expr | vec-expr | map-expr | quotedstring | word)
    list-expr = <'('> (expr / word)* <')'>
    vec-expr = <'['> (expr / word)* <']'>
    map-expr = <'{'> (expr / word)* <'}'>
    word = #\"[#a-zA-Z?'.:_\\-0-9*+$&%^.,/\\\"<>=]+\"
    quotedstring = #'\"[^\"]*\"'" :auto-whitespace whitespace-or-comments))

(def ^:private expr-transformations
  {:word         clojure.core/read-string
   :quotedstring clojure.core/read-string
   :map-expr     (fn [& args] (apply hash-map args))
   :list-expr    (fn [& args] (list* args))
   :vec-expr     (fn [& args] (vec args))})

(defn parse-string
  [s]
  (->> (e/right s)
       (e/fmap e/either expr-parser)
       (e/fmap e/either #(insta/add-line-and-column-info-to-metadata s %))
       (e/fmap e/either #(insta/transform expr-transformations %))
       (e/fmap e/either first)
       e/extract))

(comment

  (parse-string "{a 1 b 2 }")

  (parse-string "{a 1 b 2 \"}")

  (parse-string "{(}")



  )

;; ========================================

(defn read-string
  [s]
  (when (not (str/blank? s))
    (binding [*input* s]
      (parse-string s))))
