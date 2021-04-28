# Rules Engine

The supervisor works on the premise of identifying the document type, what stage it is at in the processing and 
ensuring that it is in an appropriate state.

## States & Status

The supervisor relies on a key value document existing in the Mongo document under the `doclib` property. This will 
contain flags that are set by consumers to indicate one of three states

* Exists - If the property/flag does not exist then this document has never been seen by the consumer
* False - The document has been seen by the consumer but has not completed
* True - The consumer has completed processing the document

Alongside the status flags the consumer will also test for other appropriate properties within the document to identify 
progress and validity

## Rule Sets

There are a number of rule sets currently implemented. The engine will test the document against the defined rulesets in 
the programmed order. Should a Ruleset returns a valid list of `Sendables` then an appropriate message will be sent to 
that Queue/Topic/Exchange. If a ruleset returns `None` as its response then the next ruleset will be tested until all 
rulesets are processed, at which point the message will be dropped

### Archive

Will test for:
 * recognised archive `mimetype` 
 * the existence of an `unarchived` property
 * the `unarchived` flag 
 
If it passes the test it will then attempt to instantiate an appropriate extractor using the Apache commons compress 
library. If this is not possible then an exception will be thrown and the message will be dead-lettered
 
### Chemical

Will test for:
 * recognised chemical `mimetype` 
 * the `ner` flag 

If it passes the tests it will then chain with the NER rulset

### Document

Will test for:
 * recognised office document `mimetype` 
 * the `ner` flag 

If it passes the tests it will then chain with the NER ruleset

### HTML

### NER

This ruleset is not intended to be used directly but to be chained with other rulesets. Currently these
are `Tabular`, `Text`, `HTML`, `XML` & `Chemical`.

### Tabular

### Text

### XML

### Video

### Audio

### PDF

Sends a `application/pdf` document to the image intermediates and then bounding box queues.

### Analytical

If all processing for the document has taken place and the `ANALYTICAL_SUPERVISOR` environment variable is set it 
will send the document to the analytical supervisor queue.
