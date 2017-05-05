# fern

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
let's imagine that what you really need is to configure
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

Specifically the rules of Fern evaluation are:

* Any dereferenced symbol, either `(clojure.core/deref a-symbol)` or `@a-symbol` is recursively evaluated by Fern.

* Any value quoted in the ordinary Clojure way (either with an explicit `(clojure.core/quote some-expression)` or using a quote `'some-expression`) will evaluate to the inner expression. Thus, if you wanted to include a dereferenced symbol in your Fern data, you would write `'@foo`. If you wanted to included a quoted symbol in your Fern data you would simply double up on the quotes: `'(clojure.core/quote something-quoted)'`.

* Any value of the form `(func arg1 arg2 arg3...)` is replaced by the result of doing a *Clojure* evaluation of `(func arg1 arg2 arg3...)`.

* Anything else just evaluates to itself. So `[1 foo :bar]` evaluates to a three element vector containing a number, a symbol and a keyword.

## API

Fern provides two levels of API. The core Fern API is defined in the
`fern` namespace, while the in the `fern.easy` namespace you will
find a number of convenience functions that make reading a Fern file
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

(def server-1-map (fern/evaluate 'server-1)
~~~

## Plugins

Since Fern allows you to call functions with the familiar
Clojure syntax of `(f arg1 arg2)`, it also supplies a short cut
for loading in new namespaces _from the Fern file_.
To use the shortcut, you use  `fern.easy/load-environment` in place of
`fern.easy/file->environment`.
The  `fern.easy/load-environment` is nearly identical to
`fern.easy/file->environment`: The difference is that it takes a second argument. That
will be a symbol in the Fern file whose value is a collection of
namespaces to require. For a (slightly contrived) example:

~~~
{plugins  [clojure.string]
 host-1 "server"
 host-2 "example"
 host-3 "com"
 timeout 4000

 host     (clojure.string/join "." [host-1 host-2 host-3])

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
