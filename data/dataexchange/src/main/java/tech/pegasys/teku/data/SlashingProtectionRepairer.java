/*
 * Copyright 2021 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import tech.pegasys.teku.api.schema.BLSPubKey;
import tech.pegasys.teku.data.signingrecord.ValidatorSigningRecord;
import tech.pegasys.teku.data.slashinginterchange.SigningHistory;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.logging.SubCommandLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;

public class SlashingProtectionRepairer {
  //  private final JsonProvider jsonProvider = new JsonProvider();
  private final List<SigningHistory> signingHistoryList = new ArrayList<>();
  private final Set<String> invalidRecords = new HashSet<>();
  private final SyncDataAccessor syncDataAccessor = new SyncDataAccessor();
  private final SubCommandLogger log;
  private final Spec spec;
  private Path slashingProtectionPath;

  public SlashingProtectionRepairer(final SubCommandLogger log, final Spec spec) {
    this.log = log;
    this.spec = spec;
  }

  public void initialise(final Path slashProtectionPath) {
    this.slashingProtectionPath = slashProtectionPath;
    File slashingProtectionRecords = slashProtectionPath.toFile();
    Arrays.stream(slashingProtectionRecords.listFiles())
        .filter(file -> file.isFile() && file.getName().endsWith(".yml"))
        .forEach(this::readSlashProtectionFile);
  }

  private void readSlashProtectionFile(final File file) {
    final String pubkey = file.getName().substring(0, file.getName().length() - ".yml".length());
    try {
      Optional<ValidatorSigningRecord> maybeRecord =
          syncDataAccessor.read(file.toPath()).map(ValidatorSigningRecord::fromBytes);
      if (maybeRecord.isEmpty()) {
        log.display(file.getName() + ": empty slashing protection record");
        invalidRecords.add(pubkey);
        return;
      }

      ValidatorSigningRecord validatorSigningRecord = maybeRecord.get();
      signingHistoryList.add(
          new SigningHistory(BLSPubKey.fromHexString(pubkey), validatorSigningRecord));

    } catch (Exception e) {
      if (invalidRecords.add(pubkey)) {
        display("Incomplete slashing protection data", pubkey);
      }
    }
  }

  public void updateRecords(final UInt64 slot, final UInt64 epoch, final boolean updateAllEnabled) {
    invalidRecords.forEach(pubkey -> updateValidatorSigningRecord(pubkey, epoch, slot));
    if (updateAllEnabled) {
      signingHistoryList.forEach(
          (historyRecord) -> updateValidatorSigningRecord(slot, epoch, historyRecord));
    }
  }

  private void updateValidatorSigningRecord(
      final UInt64 slot, final UInt64 epoch, final SigningHistory historyRecord) {
    if (canUpdateSignedBlock(historyRecord, slot)
        && canUpdateSignedAttestation(historyRecord, epoch)) {
      updateValidatorSigningRecord(toDisplayString(historyRecord.pubkey), epoch, slot);
    } else {
      log.error(
          "NOT UPDATED - Validator signing record was beyond supplied slot - "
              + toDisplayString(historyRecord.pubkey));
    }
  }

  private void updateValidatorSigningRecord(
      final String pubkey, final UInt64 epoch, final UInt64 slot) {
    display("Updating", pubkey);
    Path outputFile = slashingProtectionPath.resolve(pubkey + ".yml");
    Optional<ValidatorSigningRecord> existingRecord = Optional.empty();
    if (outputFile.toFile().exists()) {
      try {
        existingRecord = syncDataAccessor.read(outputFile).map(ValidatorSigningRecord::fromBytes);
      } catch (Exception e) {
        // ignore failed read, we'll write a new record
      }
    }
    final SigningHistory signingHistory =
        new SigningHistory(
            BLSPubKey.fromHexString(pubkey), new ValidatorSigningRecord(null, slot, epoch, epoch));
    try {
      syncDataAccessor.syncedWrite(
          outputFile, signingHistory.toValidatorSigningRecord(existingRecord, null).toBytes());
    } catch (IOException e) {
      log.error("Validator " + signingHistory.pubkey.toHexString() + " was not updated.");
    }
  }

  private boolean canUpdateSignedAttestation(final SigningHistory history, final UInt64 epoch) {
    return history.signedAttestations.isEmpty()
        || history.signedAttestations.stream()
                .filter(
                    signedAttestation ->
                        signedAttestation.sourceEpoch.isGreaterThan(epoch)
                            || signedAttestation.targetEpoch.isGreaterThan(epoch))
                .count()
            == 0;
  }

  private boolean canUpdateSignedBlock(SigningHistory history, final UInt64 slot) {
    return history.signedBlocks.isEmpty()
        || history.signedBlocks.stream()
                .filter(signedBlock -> signedBlock.slot.isGreaterThan(slot))
                .count()
            == 0;
  }

  private void display(final String message, final String pubkey) {
    log.display(message + " " + pubkey);
  }

  private String toDisplayString(final BLSPubKey pubkey) {
    return pubkey.toBytes().toUnprefixedHexString().toLowerCase();
  }

  public boolean hasUpdates(final boolean updateAllEnabled) {
    if (updateAllEnabled) {
      return signingHistoryList.size() + invalidRecords.size() > 0;
    }
    return invalidRecords.size() > 0;
  }
}
