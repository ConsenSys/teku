/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
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

package tech.pegasys.teku.statetransition.validation.signatures;

import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;

public abstract class SignatureVerificationService extends Service
    implements AsyncBLSSignatureVerifier {

  /**
   * A view of this service for signatures the node must verify as soon as possible, e.g. messages
   * produced by its own validators that are waiting to be broadcast. Implementations that batch
   * verifications must not hold such a signature back while gathering a larger batch.
   */
  public abstract AsyncBLSSignatureVerifier urgent();
}
