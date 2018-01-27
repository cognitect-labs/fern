# fern

[![CircleCI](https://circleci.com/gh/cognitect-labs/fern.svg?style=svg)](https://circleci.com/gh/cognitect-labs/fern)

[![Clojars Project](https://img.shields.io/clojars/v/com.cognitect/fern.svg)](https://clojars.org/com.cognitect/fern)

Fern is a simple but useful language for data. While you
can use Fern to read any kind of trusted data, Fern has semantics
that make it extremely useful for configuration data.

## Usage

Fern data is stored in the form of EDN, specifically an EDN map with symbols
for keys. Here, for example, is a simple Fern file:

~~~
{host "localhost"
 timeout 4000}
~~~

The thing that makes Fern Fern is that it provides a way
for one part of a Fern file to reference another. The
syntax for recursively evaluating a symbol in Fern is
identical to the Clojure dereference syntax. For example,
let's imagine that you need to configure
three servers. Each server listens on a different port, but
all happen to be on the same host and use the timeout value.
In this case, the Fern configuration might look like:

~~~
{host "localhost"
 timeout 4000
 server-1 {:host @host :request-timeout @timeout :port 8000}
 server-2 {:host @host :request-timeout @timeout :port 8010}
 server-3 {:host @host :request-timeout @timeout :port 8020}}
~~~

Note how the `:host` values are all set to `@host` which is a reference
to `host` which ultimately resolves to `"localhost"`.

Specifically the rules of Fern evaluation are:

* Any dereferenced symbol, either `(clojure.core/deref a-symbol)` or `@a-symbol` is recursively evaluated by Fern.

* Any value of the form `(fern/lit _keyword_ arg1 arg2 arg3...)` is replaced by the result of doing calling `(fern/literal _keyword_ arg1 arg2 arg3...)`. This provides a mechanism where you can add custom processing to your Fern files by defining a method for `fern/literal` and then using it inside of your Fern file.

* Any value of the form `(fern/fern)` is replaced by entire Fern configuration.

* Any value quoted with `fern/quote`  will be returned unprocessed
by Fern. Thus `(fern/quote a-value)` is just a long winded way of saying
`a-value`. Use `fern/quote` in those rare instances where you need to
prevent Fern evaluation. For example you can say `(fern/quote (clojure.core/deref foo))` to get the value `(clojure.core/deref foo)`
without having Fern try to resolve `foo`.

* Anything else just evaluates to itself. So `[1 foo :bar]` evaluates to a three element vector containing a number, a symbol and a keyword while `(+ 1 77)` evaluates to a three element list whose first element is the symbol `+`.

## API

Fern provides two levels of API. The core Fern API is defined in the
`fern` namespace, while the `fern.easy` namespace provides
a number of convenience functions that make reading Fern files
relatively painless. For example, `fern.easy/file->environment`
takes a path and reads the fern file at that path and returns the Fern
environment:

~~~
(require 'fern.easy)

(def e (fern.easy/file->environment "example.fern"))
~~~

Once you have a fern environment, you can use `fern/evaluate` to pull values out of it:

~~~
(require 'fern)

(def server-1-map (fern/evaluate e 'server-1)
~~~

## Plugins

Since Fern allows you to add custom processing by defining additional
methods on the `fern/literal` multimethod, it also supplies a short cut
for loading in new namespaces _from the Fern file_.
To use the shortcut, you use  `fern.easy/load-environment` in place of
`fern.easy/file->environment`.
The  `fern.easy/load-environment` is nearly identical to
`fern.easy/file->environment`: The difference is that it takes a second argument. That second argument should
be a symbol in the Fern file whose value is a collection of
namespaces to require. For a (slightly contrived) example, imagine
we had a Clojure namespace called `server-name`:

~~~
(ns server-name
  (:require [fern :as f]))

(defmethod f/literal :server-name [_ n1 n2]
  (str n1 "." n2 ".com"))
~~~

Using the plugins facility we can pull in that namespace and then
use the :server-name literal in our file.

~~~
{plugins  [server-name]
 host-1 "server"
 host-2 "example"
 timeout 4000

 host     (fern/lit :server-name host-1 host-2)

 server-1 {:host @host :request-timeout @timeout :port 8000}
 server-2 {:host @host :request-timeout @timeout :port 8010}
 server-3 {:host @host :request-timeout @timeout :port 8020}}
~~~

Then you can call fern like this:

~~~
(require 'fern.easy)

(def e (fern.easy/load-environment "example.fern" 'plugins))
~~~

And discover with `(fern/evaluate e 'host)` that your host is
`"server.example.com"`.

## Reporting Errors

Fern offers an easy way to print errors. If you get an exception from
`fern/evaluate`, `fern.easy/load-environment`, or
`fern.easy/validate!`, you can call
`fern.easy/print-evaluation-exception` to print out a nicely formatted message.


## License

Copyright © 2017 Cognitect, Inc.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
