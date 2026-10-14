# Changelog

## Upcoming Breaking Changes

## Current Releases

## Unreleased Changes

### Breaking Changes

### Additions and Improvements
 - Scheduled Gloas fork for the HOODI network at epoch 132352, which is 26 Oct 2026 17:42:48 UTC.

### Bug Fixes
 - Block production retried in the same slot after a failed `engine_forkchoiceUpdated` call now sends a new forkchoiceUpdated instead of returning the earlier failure. [#11488](https://github.com/Consensys-Incorporated/teku/pull/11488)
 - Peer selection now gives each peer identity at most one connection slot per search, even when the candidates include more than one record for it. [#11424](https://github.com/Consensys-Incorporated/teku/issues/11424)
 - Gossip subscription announcements carrying up to 500 topics in one RPC are now accepted (previously 200). [#11482](https://github.com/Consensys-Incorporated/teku/issues/11482)
 - Discovered peers whose node record advertises only a QUIC address are no longer ignored. Nodes with QUIC disabled skip them instead of dialing an address they do not have. [#11420](https://github.com/Consensys-Incorporated/teku/issues/11420)
 - Nodes with TCP disabled no longer dial, or fall back to, a discovered peer's TCP address; such candidates are skipped before peer selection and counted in the new `peer_candidate_count_total` metric. [#11421](https://github.com/Consensys-Incorporated/teku/issues/11421)
