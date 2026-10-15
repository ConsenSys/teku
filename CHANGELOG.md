# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements

### Bug Fixes
 - The node's ENR no longer advertises a TCP port when the TCP transport is disabled (`--Xp2p-tcp-enabled=false`), so peers stop dialing a port nothing listens on. [#11454](https://github.com/Consensys-Incorporated/teku/issues/11454)
 - Block production now recovers within the slot when the `engine_forkchoiceUpdated` call it relies on fails, sending a new forkchoiceUpdated instead of returning the earlier failure. [#11488](https://github.com/Consensys-Incorporated/teku/pull/11488)
