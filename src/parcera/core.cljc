(ns parcera.core
  (:require [instaparse.core :as instaparse]
            [instaparse.combinators-source :as combi]
            [instaparse.cfg :as cfg]
            [parcera.terminals :as terminal])
  #?(:cljs (:import goog.string.StringBuffer)))

(def grammar-rules
  "code: form*;

    <form>: literal | collection | reader-macro | whitespace;

    whitespace = #'([,\\s]*;.*)?([,\\s]+|$)' (* we treat comments the same way as commas *);

    (* for parsing purposes we dont consider a Set a collection since it starts
       with # -> dispatch macro *)
    <collection>: &#'[\\(\\[{]' ( list | vector | map );

    list: <'('> form* <')'> ;

    vector: <'['> form* <']'> ;

    map: <'{'> form* <'}'>;

    (* a literal is basically anything that is not a collection, macro or whitespace *)
    <literal>: &#'[^\\(\\[{#^\\'`~@\\s]' ( symbol
                                         | number
                                         | string
                                         | character
                                         | keyword
                                         );

    <keyword>: simple-keyword | macro-keyword ;

    <reader-macro>: &#'[#^\\'`~@]' ( dispatch
                                   | metadata
                                   | deref
                                   | quote
                                   | backtick
                                   | unquote
                                   | unquote-splicing
                                   | set
                                   | namespaced-map
                                   | symbolic
                                   );

    set: <'#{'> form* <'}'>;

    namespaced-map: <'#'> ( keyword | auto-resolve ) map;

    auto-resolve: '::';

    metadata: (metadata-entry whitespace)+ ( symbol
                                           | collection
                                           | tag
                                           | unquote
                                           | unquote-splicing
                                           );

    metadata-entry: <'^'> ( map | symbol | string | keyword );

    quote: <'\\''> form;

    backtick: <'`'> form;

    unquote: <#'~(?!@)'> form;

    unquote-splicing: <'~@'> form;

    deref: <'@'> form;

    <dispatch>:  function
               | regex
               | var-quote
               | discard
               | tag
               | conditional
               | conditional-splicing;

    function: <'#('> form* <')'>;

    var-quote: <'#\\''> symbol;

    discard: <'#_'> form;

    tag: <#'#(?![_?])'> symbol whitespace? (literal | collection);

    conditional: <'#?'> list;

    conditional-splicing: <'#?@'> list;

    symbolic: #'##(Inf|-Inf|NaN)'")


(def grammar-terminals
  {:character      (combi/regexp terminal/character-pattern)
   :string         (combi/regexp terminal/string-pattern)
   :symbol         (combi/regexp terminal/symbol-pattern)
   :number         (combi/regexp terminal/number-pattern)
   :macro-keyword  (combi/regexp terminal/macro-keyword)
   :simple-keyword (combi/regexp terminal/simple-keyword)
   :regex          (combi/regexp terminal/regex-pattern)})


(def grammar (merge (cfg/ebnf grammar-rules) grammar-terminals))


(def clojure
  "Clojure (instaparse) parser. It can be used as:
  - (parcera/clojure input-string)
     -> returns an AST representation of input-string
  - (instaparse/parse parcera/clojure input-string)
     -> same as above but more explicit
  - (instaparse/parses parcera/clojure input-string)
   -> returns a sequence of possible AST representations in case of ambiguity
      in input-string

   For a description of all possible options, visit Instaparse's official
   documentation: https://github.com/Engelberg/instaparse#reference"
  (instaparse/parser grammar :start :code))


(defn- code*
  "internal function used to imperatively build up the code from the provided
   AST as Clojure's str would be too slow"
  [ast #?(:clj ^StringBuilder string-builder
          :cljs ^StringBuffer string-builder)]
  (case (first ast)
    :code
    (doseq [child (rest ast)]
      (code* child string-builder))

    :list
    (do (. string-builder (append "("))
        (doseq [child (rest ast)] (code* child string-builder))
        (. string-builder (append ")")))

    :vector
    (do (. string-builder (append "["))
        (doseq [child (rest ast)] (code* child string-builder))
        (. string-builder (append "]")))

    :namespaced-map
    (do (. string-builder (append "#"))
        (doseq [child (rest ast)] (code* child string-builder)))

    :map
    (do (. string-builder (append "{"))
        (doseq [child (rest ast)] (code* child string-builder))
        (. string-builder (append "}")))

    :set
    (do (. string-builder (append "#{"))
        (doseq [child (rest ast)] (code* child string-builder))
        (. string-builder (append "}")))

    (:number :whitespace :symbolic :auto-resolve :symbol :simple-keyword
     :macro-keyword :character :string :regex)
    (. string-builder (append (second ast)))

    :metadata
    (do (doseq [child (rest (butlast ast))] (code* child string-builder))
        (code* (last ast) string-builder))

    :metadata-entry
    (doseq [child (rest ast)]
      (. string-builder (append "^"))
      (code* child string-builder))

    :quote
    (do (. string-builder (append "'"))
        (doseq [child (rest ast)] (code* child string-builder)))

    :var-quote
    (do (. string-builder (append "#'"))
        (code* (second ast) string-builder))

    :discard
    (do (. string-builder (append "#_"))
        (doseq [child (rest ast)] (code* child string-builder)))

    :tag
    (do (. string-builder (append "#"))
        (doseq [child (rest ast)] (code* child string-builder)))

    :backtick
    (do (. string-builder (append "`"))
        (doseq [child (rest ast)] (code* child string-builder)))

    :unquote
    (do (. string-builder (append "~"))
        (doseq [child (rest ast)] (code* child string-builder)))

    :unquote-splicing
    (do (. string-builder (append "~@"))
        (doseq [child (rest ast)] (code* child string-builder)))

    :conditional
    (do (. string-builder (append "#?"))
        (code* (second ast) string-builder))

    :conditional-splicing
    (do (. string-builder (append "#?@"))
        (code* (second ast) string-builder))

    :deref
    (do (. string-builder (append "@"))
        (doseq [child (rest ast)] (code* child string-builder)))

    :function
    (do (. string-builder (append "#("))
        (doseq [child (rest ast)] (code* child string-builder))
        (. string-builder (append ")")))))


(defn code
  "Transforms your AST back into code

   ast: The nested sequence of [:keyword & content] which MUST follow the
        same structure as the result of `(parcera/clojure input-string)`

   Returns a string representation of the provided AST

   In general (= input (parcera/code (parcera/clojure input)))"
  [ast]
  (let [string-builder #?(:clj (new StringBuilder)
                          :cljs (new StringBuffer))]
    (code* ast string-builder)
    (. string-builder (toString))))

; Successful parse.
; Profile:  {:create-node 594, :push-full-listener 2, :push-stack 594,
;            :push-listener 592, :push-result 257, :push-message 257}
; "Elapsed time: 63.203691 msecs"
#_(time (clojure (str '(ns parcera.core
                         (:require [instaparse.core :as instaparse]
                                   [clojure.data :as data]
                                   [clojure.string :as str])))
                 :trace true))

#_(instaparse/disable-tracing!)
