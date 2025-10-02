# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

## [1.0.1] - 2025-10-02

- Breakout cronut-integrant and cronut-javax into separate project repositories
- Update tests re: trigger and job key identity

## [1.0.0] - 2025-07-23

Significant rewrite of internals, splitting into three projects.

- cronut project supports Quartz 2.5.0 and Jakarta
- cronut-javax project supports Quartz 2.4.0 and Javax
- cronut-integrant contains integrant bindings that were previously in core project
- introduce ability to mutate scheduler, pausing, resuming, stopping, removing jobs and triggers
- add ability to specify disallow-concurrent-execution at a job level
- **BREAKING**: configuration for global concurrency controls renamed to :concurrent-execution-disallowed?

## [0.2.7] - 2020-09-30

Changes in preperation of more active development:

- Shift to factorhouse organisation on github and clojars
- Contract troy-west.cronut ns to simply cronut
- Change to MIT license

## [0.2.6] - 2020-07-14

- Suppport global :disallowConcurrentExecution option (you will want to understand misfires if you use this).

## [0.2.1 - 0.2.5] - 2019-06-19

- Minor dependency bumps and README improvements (#9)

## [0.2.0] - 2018-07-23

- Breaking: remove :time-zone configuration at a scheduler level (see issue #5 for more)

## [0.1.1] - 2018-06-15

- Minor bugfix for zero-argument (run once, immediately) interval execution
- Updated Readme
- Added tests and CircleCI integrations

## [0.1.0] - 2018-06-08

- Initial Release
  - Supports Cron and Simple schedules
  - Extensible trigger-builder multi-method
  - Data readers for trigger, cron, and interval
  - Basic integration tests
