# parcera

[![Build Status](https://travis-ci.com/carocad/parcera.svg?branch=master)](https://travis-ci.com/carocad/parcera)
[![Clojars Project](https://img.shields.io/clojars/v/carocad/parcera.svg)](https://clojars.org/carocad/parcera)

Grammar-based Clojure(script) parser.

Parcera can safely read any Clojure file without any code evaluation.

Parcera uses the wonderful [Instaparse](https://github.com/Engelberg/instaparse) as its
parsing engine and focuses entirely on the grammar definition instead. For a
full explanation of the options available for a parser please visit Instaparse website.

## usage

```clojure
(ns example.core
  (:require [parcera.core :as parcera]
            [instaparse.core :as instaparse]))

;;parse clojure code from a string
(parcera/clojure (str '(ns parcera.core
                         (:require [instaparse.core :as instaparse]
                                   [clojure.data :as data]
                                   [clojure.string :as str]))))

;; => returns a data structure with the result from the parser
[:code
 [:list
  [:symbol "ns"]
  [:whitespace " "]
  [:symbol "parcera.core"]
  [:whitespace " "]
  [:list
   [:simple-keyword ":require"]
   [:whitespace " "]
   [:vector
    [:symbol "instaparse.core"]
    [:whitespace " "]
    [:simple-keyword ":as"]
    [:whitespace " "]
    [:symbol "instaparse"]]
   [:whitespace " "]
   [:vector [:symbol "clojure.data"] [:whitespace " "] [:simple-keyword ":as"] [:whitespace " "] [:symbol "data"]]
   [:whitespace " "]
   [:vector [:symbol "clojure.string"] [:whitespace " "] [:simple-keyword ":as"] [:whitespace " "] [:symbol "str"]]]]]

;; convert an AST back into a string
(parcera/code [:symbol "ns"])
;; "ns"
```

### notes
There are some restrictions as to how much can a parser do. In my experience, these restrictions
are related to some [semantic context-sensitivity](http://blog.reverberate.org/2013/09/ll-and-lr-in-context-why-parsing-tools.html).
which the Clojure reader has embedded into itself. In general I have found the following ones:
    - `parcera` doesnt check that a map contains an even number of elements. This is specially difficult
      to do since Clojure supports the discard macro `#_ form` which is a valid element but "doesnt count as one"
    - `parcera` doesnt check if a map has repeated keys
    - `parcera` doesnt check if a set has repeated elements
