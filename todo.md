Tasks

* X Allow evaluate to use the whole config for strange loopiness

* Look at metadata
  * Lets write some tests

* function to extract good error messages from exceptions
    * Did you mean foo or bar from exceptions when helpful and possible

* Vase specific
   * Database insertion more explicit
   * Special actions at startup for schema (request 0 interceptors)


* X Include source metadata on values when reading.

X * Make Environment Assoicative

* X Need a better message for the following case

    user> (f/evaluate e 'vase/service)

    IllegalArgumentException No method in multimethod 'literal' for dispatch value: vase/respond  clojure.lang.MultiFn.getFn (MultiFn.java:156)

* X Don't require the plugins key if it is not there.

    [7:54 AM] Michael T. Nygard: Another todo item: right now, if a "plugins" key is suplied to fern.easy/load-environment, then every file is _required_ to have that key or we get an exception. Seems like it should be optional for the user.
* X Come up with a good name for convenience namespace name

* X Rename fern.core to ?? and generalize it
     * Clean up error handling
     * startup -> load-plugins or something
     * Dont assume vase.plugins as the symbol


* X Make deref'ing symbols explicit with @
