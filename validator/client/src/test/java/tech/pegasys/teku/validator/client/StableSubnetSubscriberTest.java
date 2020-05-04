/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.primitives.UnsignedLong;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class StableSubnetSubscriberTest {
  private final Map<BLSPublicKey, Validator> validators =
      new HashMap<>(
          Map.of(
              BLSPublicKey.random(0), mock(Validator.class),
              BLSPublicKey.random(1), mock(Validator.class)));
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private StableSubnetSubscriber stableSubnetSubscriber;

  @BeforeEach
  void setUp() {

    stableSubnetSubscriber = new StableSubnetSubscriber(validatorApiChannel, validators);
  }

  @Test
  void shouldCreateEnoughSubscriptionsAtStart() {
    verify(validatorApiChannel).updateRandomSubnetSubscriptions(argThat(arg -> arg.size() == 2));
  }

  @Test
  void shouldLowerNumberOfSubscriptionsWhenNumberOfValidatorsDecrease() {
    verify(validatorApiChannel).updateRandomSubnetSubscriptions(argThat(arg -> arg.size() == 2));

    validators.remove(BLSPublicKey.random(0));

    stableSubnetSubscriber.onSlot(UnsignedLong.ONE);

    verify(validatorApiChannel, times(2))
        .updateRandomSubnetSubscriptions(argThat(arg -> arg.size() == 1));
  }

  @Test
  void shouldIncreaseNumberOfSubscriptionsWhenNumberOfValidatorsIncrease() {
    verify(validatorApiChannel).updateRandomSubnetSubscriptions(argThat(arg -> arg.size() == 2));

    validators.put(BLSPublicKey.random(2), mock(Validator.class));

    stableSubnetSubscriber.onSlot(UnsignedLong.ONE);

    verify(validatorApiChannel, times(2))
        .updateRandomSubnetSubscriptions(argThat(arg -> arg.size() == 3));
  }

  @Test
  @SuppressWarnings("unchecked")
  void shouldReplaceExpiredSubscriptionsWithNewOnes() {
    ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

    StableSubnetSubscriber stableSubnetSubscriber =
        new StableSubnetSubscriber(
            validatorApiChannel, Map.of(BLSPublicKey.random(0), mock(Validator.class)));

    ArgumentCaptor<Map<Integer, UnsignedLong>> firstSubscriptionUpdate =
        ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<Map<Integer, UnsignedLong>> secondSubscriptionUpdate =
        ArgumentCaptor.forClass(Map.class);

    verify(validatorApiChannel).updateRandomSubnetSubscriptions(firstSubscriptionUpdate.capture());

    assertThat(firstSubscriptionUpdate.getValue()).hasSize(1);

    UnsignedLong firstUnsubscriptionSlot =
        new ArrayList<>(firstSubscriptionUpdate.getValue().values()).get(0);

    stableSubnetSubscriber.onSlot(firstUnsubscriptionSlot.minus(UnsignedLong.ONE));

    verifyNoMoreInteractions(validatorApiChannel);
    stableSubnetSubscriber.onSlot(firstUnsubscriptionSlot);

    verify(validatorApiChannel, times(2))
        .updateRandomSubnetSubscriptions(secondSubscriptionUpdate.capture());

    UnsignedLong secondUnsubscriptionSlot =
        new ArrayList<>(secondSubscriptionUpdate.getValue().values()).get(0);

    assertThat(firstUnsubscriptionSlot).isNotEqualByComparingTo(secondUnsubscriptionSlot);
    // Can only verify unsubscription slot have changed and not the subnet id,
    // since subnet id can randomly be chosen the same
  }
}
