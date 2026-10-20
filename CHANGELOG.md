# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
 - Gossip signature verification now gathers larger batches and verifies signatures over the same message together, lowering CPU usage at the cost of up to ~10 ms added gossip validation latency. Use `--Xp2p-batch-verify-signatures-mode=LEGACY` to restore the previous batching behaviour. In both modes, signatures re-verified after a failed batch now honour `--Xp2p-batch-verify-signatures-strict-thread-limit-enabled` and a task that throws no longer fails the rest of its batch.

### Bug Fixes
 - Reduced the memory held by gossip blocks waiting for validation, limited their total size, and blocks too far in the future are now ignored before they are decoded. [#11503](https://github.com/Consensys-Incorporated/teku/pull/11503)
 - The node's ENR no longer advertises a TCP port when the TCP transport is disabled (`--Xp2p-tcp-enabled=false`), so peers stop dialing a port nothing listens on. [#11454](https://github.com/Consensys-Incorporated/teku/issues/11454)
 - Block production now recovers within the slot when the `engine_forkchoiceUpdated` call it relies on fails, sending a new forkchoiceUpdated instead of returning the earlier failure. [#11488](https://github.com/Consensys-Incorporated/teku/pull/11488)
 - Slashing protection imported through the keymanager API for a key that was deleted earlier in the same run is now used when the key is re-imported, instead of being overwritten by the record cached before the delete. Slashing protection sent with a keystore that is already loaded is no longer applied, matching the `duplicate` response. [#11388](https://github.com/Consensys-Incorporated/teku/issues/11388)
 - `ExecutionPayloadEnvelopesByRange` only serves envelopes of payloads that are part of the chain defined by the current head, as required by the spec. Envelopes of finalized blocks whose payload the chain did not build on are no longer retained, and such envelopes received while syncing are dropped instead of imported. Such envelopes already persisted by earlier versions are not removed, which only affects Gloas devnet nodes upgraded in place. [#11246](https://github.com/Consensys-Incorporated/teku/issues/11246)