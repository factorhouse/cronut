# Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

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
