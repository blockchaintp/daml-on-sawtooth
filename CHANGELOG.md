# CHANGELOG

## v1.3.10

* fix: Satisfy checkstyle, make spelling consistent
* fix: Add Javadoc
* A very much more specific exception
* Supress this warning
* Guard expensive log operations
* docs: update build instructions
* build: update build to match standard patterns
* build: add a direct pom reference to btp-releases repository
* refactor: remove timekeeper,sawtooth-mttp,sawtooth-utils,and keymanager
* refactor: eliminate daml-on-sawtooth-protobufs module
* refactor: separate out sawtooth-utils and rearrange protos
* refactor: relocate VersionedEnvelope in prep for isolation
* refactor: isolate VersionedEnvelope
* refactor: isolate keymanager
* refactor: extract BaseKeyManager from km classes
* refactor: simplify var declarations
* refactor(sawtooth-daml-common): isolate MTTTransactionProcessr
* refactor: correct use of wrong exception
* refactor(sawtooth-daml-common): remove classes which are now correct upstream
* refactor(timekeeper): completely isolate timekeeper classes into the timekeeper pkg

## v1.3.9

* fix: correct issues arising from sonar checks
* refactor: correct sonar bugs
* refactor: address checkstyle issues now that it works
* build: update checkstyle configuration

## v1.3.8

* fix(timekeeper): correct initial backoff counter increment

## v1.3.7

* fix(timekeeper): correct initial backoff counter increment
* fix: correct logging levels to reduce noice at info level
* Correct docker/run.sh to deal with paths with empty spaces
* Correct build.sh to deal with paths with empty spaces

## v1.3.6

* fix(timekeeper): version and limit data growth
* feat(sawtooth-daml-rpc): add jwks authentication method SXT-504
* feat(timekeeper): add exponential back off to timekeeper

## v1.3.5

* refactor(timekeeper): make time calculations more correct and clearer SXT-552
* fix: correct a bug in timekeeper arg parsing
* fix(rpc): adjust timemodel parameters to compensate for a bug in SDK 1.3.0
* fix(rpc): disable dependent transactions SXT-552
* build(pre-commit): update config SXT-552

## v1.3.4

* fix(rpc): enable rsa256 auth method SXT-511

## v1.3.3

* fix(zmqeventhandler): if no beginafter is set then start at the beginning

## v1.3.2

* fix(sawtooth-daml): improve logging
* fix(sawtooth-daml-tp): improve normal exception handling
* fix(sawtooth-daml-tp): throw InvalidTransactionException when missing a leaf key
* refactor(sawtooth-daml-tp): make leaf assembly less duplicative
* fix(sawtooth-daml-tp): defer events until after all other activities
* fix(protobufs): add hash checking to VersionedEnvelope and DamlTransactionFragment
* fix(sawtooth-daml-common): improvements to multipart start parsing
* fix(sawtooth-daml-rpc): correct maxopsperbatch and maxOutstandingBatches cli arg parsing
* fix(sawtooth-daml-tp): switch batch to 256k
* fix(sawtooth-daml-tp): improve logging
* fix(sawtooth-daml-common): use dependent transactions
* fix(sawtooth-daml-tp): improve reporting of ValidationFailed errors
* fix(sawtooth-daml-tp): adjust fragment size down to 128k
* fix(timekeeper): add the ability to configure the tk period
* fix(various): clean up DAML namespaces and fix build
* fix(events): send events ids to be fetched asynchronously
* fix(sawtooth-daml-tp): correct handling of fragments
* fix(sawtooth-daml-rpc): adjust default max-ops-per-batch and max-outstanding-batches
* fix(processor): adjust logging
* fix(sawtooth-daml-rpc): add the ability to fragment large transactions
* fix(sawtooth-daml-common): add multipart events
* fix(sawtooth-daml-rpc): specify the entire namespace as the write target
* fix(sawtooth-daml-common): split damlstate k/v's across multiple stl k/v's
* fix(sawtooth-daml-commonn): add StreamContext override, increase TIME_OUT
* fix(daml-rpc): compress batches across the wire

## v1.3.1

* fix(sawtooth-daml-rpc): patch ApiPackageManagementService to extend the timeout to 600s

## v1.3.0

* Tweak punctuation and remove unnecessary phrase
* Capitalization tweaks courtesy of Mac checker
* test(integration): exclude consistently failing tests
* test(integration): add TAP result processing
* feat(sdk): upgrade to DAML SDK 1.3.0

## v1.2.99

* refactor(messaging): move messaging up to sawtooth
* refactor(imports): organize imports
* refactor(tp): move ...processor.impl classes to ...processor
* refactor(timekeeper): move ...timekeeper.utils classes to ...timekeeper
* refactor(daml.utils): move ...daml.utils classes to ...daml
* style(checkstyle): fix checkstyle violations
* fix(logging): migrate to slf4j logging
* Update checkstyle plugin version
* Update pre-commit config and fix checkstyle
* Update pre-commit
* Add probot-stale config
* Fix format
* Fix bug in cli scala
* Recommend approach to generate JWT token
* Re-phrase statement
* Change assists to assistant
* Instruction to use daml cli
* Prepare for DA artifacts only being published to maven central rather than public bintray

## v0.2.3

* Delay rpc and test start up by 30s
* Add Shay Gordon, Remove Paul Sitoh
* Set postgres to use host_auth trust
* Add pre-commit config

## v0.2.2

* Catch as run time exception
* Add newlines
* Add newline
* Add new line
* Update AUTH README
* Update AuthService options
* Remove unused import
* Add default auth settings
* Bugfix: Update algorithm EC256
* sxt-271: Instruction to start auth services
* SXT-272: Fix token generator
* Using sawtooth private key
* Add option to turn on AuthService
* Update README
* Fix public key handling
* Resolve checkStyle issues - add required spacing around colons and brackets - define magic values as class instance level constants - make methods private which need not be public
* add javadocs and final modifiers to resolve checkStyle issues
* Script to start and stop local daml-on-sawtooth
* Update to match incoming changes
* concurrent run of 1
* exclude TransactionScaleIT from test suite run
* only exclude TimeIT, set scale factor to 50% (used by lots of parties it and transaction scale tests)
* address start import warning
* Address warnings, refactor to avoid code duplication, use singleton instance of SubmissionResult to avoid spurious errors
* Return healthy case object rather than abstract class
* Address warnings
* Upgrade to SDK 0.13.41 - Implement health services in read and write services - Async interfaces for party and package management - TimeModel change in package path
* Update README
* Fix JWT generators
* Fix typo so name match bash script
* Add bash script to trigger jwtgenerator
* Update RPC Pom
* Add cli to JWTgenerator
* Fix pub/private key handling
* Implement JWTGenerator
* Implementation of DAMLAuthSerivce
* Add implementation of AuthService
* Add authService from digital assets
* Implementing AuthService
* Relocate ci builds to away from master node
* batch submission was returning overloaded on unknown transactions
* Hints may not be sent and we need to pick a party id before submission
* Improve the daml-rpc and daml-tp parallel behavior
* Update dependencies to version recommended

## v0.2.0

* Adjust defaults to match timekeeper defaults
* UNKNOWN is not a terminal state
* Update to DAML SDK 0.13.37
* Additional error catching
* Remove synchronization
* Update to SDK 13.29
* Switch to using a thread-safe ZmqStream impl
* Couple of minor markdown edits
* add intellij related extensions to gitignore
* Fix typo
* Update README
* Move BUILD.md content to README.md
* Remove address contention b/w input and output
* Add and use MTTransactionProcessor

## v0.1.3

* Fixed BUILD.md link
* Remove synchronization on sendToValidator
* Adjust default TimeModel to tighter values
* Update testtool SDK to 13.21
* Add some detailed logging wrt timemodel
* Update to use sdk 0.13.18
* Change to using protobuf JsonFormat
* Fix Java spacing.
* Temporary fix
* Ignore node modules
* SXT-113 Update dependencies and add resolutions
* Remove noop-transaction-processor
* Refactor submissions and correct party allocation
* Correct Paul Sitoh in CODEOWNERS
* Update instruction for user to customise noxy
* Change tracer-ui compose port to expose directive

## v0.1.2

* Remove noop-transaction-processor
* Refactor submissions and correct party allocation
* Correct Paul Sitoh in CODEOWNERS
* Add Brian Healey
* Add Marcin Ziolek
* Add Jussi Maki as CODEOWNER
* Updates to BUILD.md instructions
* correct build image name in build.sh
* Update README.md
* Add OSS related scaffolding documents
* Update README.md
*  mount /dev/urandom
* Fix SecureRandom generation
* Execute stream send before completionstage
* move verify
* batch mode mvn clean
* Bump sawtooth SDK to official v0.1.3
* fix build-helper version
* Resolve checkstyle issues
* Add verify goal to trigger checkstyle
* if offset is unknown start at 0
* Remove tracer-ui from test
* Add build of images
* Include the daml-test.yaml
* Add integration tests to Jenkinsfile
* Add JAVA_ARGS to entrypoints
* Add ledger-api-testtool docker
* Add a docker build for the ledger-api-testtool
* Update Readme
* Implement party allocation on RPC
* Implement party adding on the TP
* Add SawtoothDamlOperation and SawtoothDamlParty
* Format rpc.proto
* Remove outdated README
* Update RPC for new SDK
* Update TP for new SDK
* Update SDK to 0.13.16
* Write keys as hex
* Fix nonce to properly generate
* Add reading of ledger-id from ledger
* Reduce logging levels
* Remove LRU cache, no substantial effect
* Add an LRU cache, not MT safe
* Re-enable compression
* Disable compression
* Add timings to compression
* improve concurrency in submission
* Correct test failure
* set multiple values at once
* Logging refinements
* Add performance timing, reduce log noise
* Support restarts using for reset index server
* Pull timemodel from ledger
* Reissue the request
* Reinitialize ZmqStream in case it is early
* Set default timeout=10, return on NO_RESOURCE
* Resolve NPE, improve logging
* Fix checkstyle
* Get TimeModel from chain or default
* Add timemodel storing at the backend
* Add catchup handling to SawtoothReadService
* Add logging to batch submission
* Fix submission status check
* unwind multi k/v assignment
* Update to sdk 13.5
* Improve client exception handling
* return to 12.25
* Adjust time model
* Add some record time logging
* Adjust time model for 1m/1m/5m
* Update sdk to 13.0

## v0.1.1

* correct branch checkout
* Validation of log keys corrected
* Fix missing log multipart key
* Deal with empty values properly
* Allow empty values vs nulls
* Fix checkstyle
* Set an arbitrary value for command dedups
* Return option empty when size=0 and is dedup
* Fix checkstyle violations
* fix when uncompressing size=0
* Compress log event
* Fix missed compression
* Multipart log entries
* Add multipart key to package upload
* Fix checkstyle
* Fix checkstyle problem
* Refactor DamlState K/V's to use a multipart key
* Compress DamlStateValues,and DamlLogEntry in state
* Add input addresses to output after populated
* Add dedupKey and input addresses to output
* Log output address on rpc
* Fix return when the value is a 0 length ByteString
* Log keyvalue types
* Add package addresses as potential input
* Add more logging
* Add log entry list to input addresses
* Add missing input address for global time
* Improve Logging
* Disable pmd ruleset
* Remove UnnecessaryFullyQualifiedName rule
* Refactor provide all requested state entries
* Fix participantId and KeyManager
* Update for sdk 12.25
* More license headers
* Add copyright headers to java files
* Add copyright headers to xml, docker and sh
* trigger a daily build
* Cleanup Jenkinsfile
* Upgrade sdk to 0.12.24
* Replace protobuf direct calls with kvutil un/pack
* Ensure transaction elements are shown properly
* Add log entry address to output list
* Add a secondary counter
* Remove unused imports
* Fix to enable transactions to be listed properly.
* Update to use daml sdk 0.12.22
* Fix so it can read the transactions from RPC
* Remove unguarded and unnecessary logging
* Add demo dar loading
* Reduce some logging noise
* Add tracer-ui
* Fix routing
* Remove the flag for manual run
* Remove superfluous Jenkin files
* Add tracer-ui composer
* Refine build sequence
* Remove stage where we tag and push images out
* Use jenkins provided settings.xml
* Exclude sources and javadoc jars from assembly
* tidy pom files, fix a javadoc error
* refactor checkstyle, add pmd, and resolve issues
* upgrade protobuf, and remove log4j
* Refactor for tracer support
* Enhance KeyManager, add DirectoryKeyManager
* Add Sawtooth RESTFul transaction tracer.
* Add rest object
* Refine logging
* Refactor event subscription
* Change read global time record at TP
* Fix checkstyle variation
* Separated out timekeeper according to design
* Correct formatting and jvm selection
* Remove unused imports
* Add docker push
* Fix settings.xml usage, do mvn clean
* Add docker push
* Fix settings.xml usage, do mvn clean
* Separated out timekeeper according to design
* Correct formatting and jvm selection
* Remove unused imports
* Add docker push
* Fix settings.xml usage, do mvn clean
* Implement getLedgerInitialConditions
* Collapse DamlStateKeys into a single address space
* Add turning timekeeper events into heartbeats
* Add mvn deploy
* change to use assemblies instead of shade
* Add daml-tp and daml-rpc to daml-local
* Add initial local net configuration
* Separated out timekeeper according to design
* Correct formatting and jvm selection
* Remove unused imports
* Implement getLedgerInitialConditions
* Add docker push
* Fix settings.xml usage, do mvn clean
* Ops: Fix jenkinsfile
* Refactor dockerfiles,add Jenkinsfile
* Add turning timekeeper events into heartbeats
* Collapse DamlStateKeys into a single address space

