(ns ankha.core
  (:refer-clojure :exclude [empty? inspect])
  (:require
   [om.core :as om :include-macros true]
   [om.dom :as dom :include-macros true]
   [clojure.string :as string]
   [goog.object :as object]))

(enable-console-print!)

;; ---------------------------------------------------------------------
;; Protocols

(defprotocol IInspect
  (-inspect [this]
    "Return a React or Om compatible representation of this."))

;; ---------------------------------------------------------------------
;; Utilities

(defn- empty?
  "Return true if x is an empty js/Object or empty Clojure collection."
  [x]
  (if (object? x)
    (object/isEmpty x)
    (clojure.core/empty? x)))

(defn- record?
  "Return true if x satisfies IRecord, false otherwise."
  [x]
  (satisfies? IRecord x))

(defn record-name
  "Return the name of a Record type."
  [r]
  (let [s (pr-str r)]
    (subs s 0 (.indexOf s "{"))))

(defn record-opener
  "Return an opener for a Record type."
  [r]
  (str (record-name r) "{"))

;; ---------------------------------------------------------------------
;; View helpers

(declare collection-view)

(defn literal [class x]
  (dom/span #js {:className class :key x}
    (pr-str x)))

(defn coll-view [data opener closer class]
  (om/build collection-view data
    {:opts {:opener opener :closer closer :class class}}))

(defn inspect [x]
  (cond
    (satisfies? IInspect x)
    (-inspect x)
    (fn? x)
    (literal "function" x)
    :else
    (literal "literal" x)))

(defn associative->dom
  [data {:keys [entry-class key-class val-class]}]
  (into-array
    (for [[k v] data]
      (dom/li #js {:key (str [k v])}
        (dom/div #js {:className (str "entry " entry-class)
                      :style #js {:position "relative"}}
          (dom/span #js {:className (str "key " key-class) 
                         :style #js {:display "inline-block"
                                     :verticalAlign "top"}}
            (inspect k))
          (dom/span #js {:style #js {:display "inline-block"
                                     :width "1em"}})
          (dom/span #js {:className (str "val " val-class)
                         :style #js {:display "inline-block"
                                     :verticalAlign "top"}}
            (inspect v)))))))

(defn sequential->dom [data]
  (into-array
    (for [[i x :as pair] (map-indexed vector data)]
      (dom/li #js {:className "entry"
                   :key (str pair)}
        (inspect x)))))

(defn coll->dom [data]
  (cond
   (map? data)
   (associative->dom data {:entry-class "map-entry"
                           :key-class "map-key"
                           :val-class "map-val"})
   (object? data)
   (let [;; Avoid zipmap to preserve key order.
         ks (object/getKeys data)
         vs (object/getValues data)
         m (map vector ks vs)]
     (associative->dom m {:entry-class "object-entry"
                          :key-class "object-key"
                          :val-class "object-val"}))
   :else
   (sequential->dom data)))

(defn- toggle-button [owner {:keys [disable?]}]
  (dom/button #js {:className "toggle-button"
                   :disabled disable?
                   :onClick
                   (fn [_]
                     (om/update-state! owner :open? not))
                   :style #js {:display "inline-block"
                               :verticalAlign "top"
                               :border "none"
                               :background "none"
                               :cursor "pointer"
                               :outline "none"
                               :fontWeight "bold"
                               :padding "0"
                               :opacity (if disable? "0.5" "1.0")}}
    (if (om/get-state owner :open?) "-" "+")))

;; ---------------------------------------------------------------------
;; Main component

(defn collection-view
  [data owner {:keys [class opener closer] :as opts}]
  (reify
    om/IInitState
    (init-state [_]
      {:vacant? (empty? data)
       :open? (and (not (false? (:open? opts)))
                   (not (empty? data)))})

    om/IRenderState
    (render-state [_ {:keys [open? vacant?]}]
      (dom/div #js {:className class}
        (toggle-button owner {:disable? vacant?})
        
        (dom/span #js {:className "opener"
                       :style #js {:display "inline-block"}}
          opener)

        (dom/ul #js {:className "values"
                     :style #js {:display (if open? "block" "none")
                                 :listStyleType "none"
                                 :margin "0"}}
          (coll->dom data))

        (dom/span #js {:className "ellipsis"
                       :style #js {:display (if (or open? vacant?)
                                              "none"
                                              "inline")}}
          "…")

        (dom/span #js {:className "closer"
                       :style #js {:display (if open?
                                              "block"
                                              "inline-block")}}
          closer)))))

(defn inspector
  ([data owner]
     (inspector data owner {:opts {:class "inspector"}}))
  ([data owner {:keys [class] :or {class "inspector"} :as opts}]
     (reify
       om/IRender
       (render [_]
         (dom/div #js {:className class
                       :style #js {:fontFamily "monospace"
                                   :whiteSpace "pre-wrap"
                                   :width "100%"
                                   :overflowX "scroll"}}
           (inspect data))))))

;; ---------------------------------------------------------------------
;; IInspect Implementation

(extend-protocol IInspect
  Keyword
  (-inspect [this] (literal "keyword" this))

  Symbol
  (-inspect [this] (literal "symbol" this))

  PersistentArrayMap
  (-inspect [this]
    (coll-view this "{" "}" "map persistent-array-map"))

  PersistentHashMap
  (-inspect [this]
    (coll-view this "{" "}" "map persistent-hash-map"))

  PersistentVector
  (-inspect [this] (coll-view this "[" "]" "vector"))

  PersistentHashSet
  (-inspect [this] (coll-view this "#{" "}" "set persistent-hash-set"))

  PersistentTreeSet
  (-inspect [this] (coll-view this "#{" "}" "set persistent-tree-set"))

  List
  (-inspect [this] (coll-view this "(" ")" "list"))

  LazySeq
  (-inspect [this] (coll-view this "(" ")" "seq lazy-seq"))

  KeySeq
  (-inspect [this] (coll-view this "(" ")" "seq key-seq"))

  ValSeq
  (-inspect [this] (coll-view this "(" ")" "seq val-seq"))

  PersistentArrayMapSeq
  (-inspect [this] (coll-view this "(" ")" "seq persistent-array-map-seq"))

  Range
  (-inspect [this] (coll-view this "(" ")" "seq range"))

  om/IndexedCursor
  (-inspect [this]
    (coll-view this "[" "]" "vector indexed-cursor"))

  om/MapCursor
  (-inspect [this]
    (if (record? (om/value this))
      (coll-view this (record-opener this) "}" "record map-cursor")
      (coll-view this "{" "}" "map map-cursor")))

  js/RegExp
  (-inspect [this] (literal "regexp" this))

  function
  (-inspect [this] (literal "function" this)) 

  number
  (-inspect [this] (literal "number" this))

  string
  (-inspect [this] (literal "string" this))

  boolean
  (-inspect [this] (literal "boolean" this))

  array
  (-inspect [this] (coll-view this "#js [" "]" "array"))

  object
  (-inspect [this] (coll-view this "#js {" "}" "object"))

  nil
  (-inspect [this] (literal "nil" this)))
