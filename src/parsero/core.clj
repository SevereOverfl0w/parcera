(ns parsero.core
  (:require [instaparse.core :as instaparse]
            [clojure.core.strint :as strint]))

;; NOTE: several characters are not allowed according to clojure reference.
;; https://clojure.org/reference/reader#_symbols
;; EDN reader says otherwise https://github.com/edn-format/edn#symbols
(def valid-characters "[\\w.*+\\-!?$%&=<>\\':#]+")
;; symbols cannot start with number, :, #
;; / is a valid symbol as long as it is not part of the name
(def symbol-head "(?![0-9:#])")

(def grammar
  (strint/<<
    "file: form*

    <forms>: form*;

    <form>: (literal /  symbol)
        | list
        | vector
        | map
        (* | reader_macro *)
        ;

    list: <'('> forms <')'> ;

    vector: <'['> forms <']'> ;

    map: <'{'> (form form)* <'}'> ;

    set: <'#{'> forms <'}'> ;

    <literal>:
          number
        | STRING
        | character
        | NIL
        | BOOLEAN
        | keyword
        | <COMMENT>
        (* | param_name *)
        ;

    symbol: SIMPLE_SYMBOL | NAMESPACED_SYMBOL;

    keyword: SIMPLE_KEYWORD | NAMESPACED_KEYWORD | MACRO_KEYWORD;

    number:
          DOUBLE
        | RATIO
        | LONG
        ;

    character:
          NAMED_CHAR
        | UNICODE_CHAR
        ;

    UNICODE_CHAR: #'\\\\u[0-9D-Fd-f]{4}';

    NAMED_CHAR: #'\\\\(newline|return|space|tab|formfeed|backspace|c)';

    (* Lexers -------------------------------------------------------------- *)

    STRING : #'^\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"';

    NIL : 'nil';

    BOOLEAN : 'true' | 'false' ;

    SIMPLE_SYMBOL: #'(~{symbol-head}~{valid-characters})|\\/';

    NAMESPACED_SYMBOL: #'~{symbol-head}~{valid-characters}\\/~{valid-characters}';

    SIMPLE_KEYWORD: #':~{valid-characters}';

    NAMESPACED_KEYWORD: #':~{valid-characters}\\/~{valid-characters}';

    MACRO_KEYWORD: #'::~{valid-characters}';

    DOUBLE: #'([-+]?[0-9]+(\\.[0-9]*)?([eE][-+]?[0-9]+)?)(M)?'

    RATIO: #'([-+]?[0-9]+)/([0-9]+)'

    LONG: #'([-+]?)(?:(0)|([1-9][0-9]*)|0[xX]([0-9A-Fa-f]+)|0([0-7]+)|([1-9][0-9]?)[rR]([0-9A-Za-z]+)|0[0-9]+)(N)?'

    COMMENT: #';.*';


    (* TODO ---------------------------------

     reader_macro:
          lambda
        | meta_data
        | regex
        | var_quote
        | host_expr
        | set
        | tag
        | discard
        | dispatch
        | deref
        | quote
        | backtick
        | unquote
        | unquote_splicing
        | gensym
        ;

    (* TJP added '&' (gather a variable number of arguments) *)
    quote: '\\'' form;

    backtick: '`' form;

    unquote: '~' form;

    unquote_splicing: '~@' form;

    tag: '^' form form;

    deref: '@' form;

    gensym: SYMBOL '#';

    lambda: '#(' form* ')';

    meta_data: '#^' (map form | form);

    var_quote: '#\\'' symbol;

    host_expr: '#+' form form;

    discard: '#_' form;

    dispatch: '#' symbol form;

    regex: '#' STRING;

    param_name: PARAM_NAME;

    *)"))

;; TODO: PARAM_NAME: '%' ((('1'..'9')('0'..'9')*) | '&')?;

(def parser (instaparse/parser grammar :auto-whitespace :comma))

(parser "(defn foo
          \"I don't do a whole lot.\"
          [x]
          (println x 9.78 \"Hello, World!\"))")

(parser (slurp "./src/parsero/core.clj"))

;; TODO: is this a bug ?
#_(def foo.bar "hello")
;; TODO: is this a bug ?
#_(def . "hello")

;; TODO: is this a bug ?
#_(defn foo.bar [a.b]) ;; not valid ... why ?

;; TODO: is this a bug ? ;; a keyword starting with a number
;; :1hello.world

(def hello #(println %1))
