/*
 * Copyright 2019 ConsenSys AG.
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

package tech.pegasys.teku.datastructures.networking.libp2p.rpc;

import static tech.pegasys.teku.util.config.Constants.MAX_REQUEST_BLOCKS;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ssz.backing.SszList;
import tech.pegasys.teku.ssz.backing.tree.TreeNode;
import tech.pegasys.teku.ssz.backing.type.AbstractDelegateType;
import tech.pegasys.teku.ssz.backing.type.BasicViewTypes;
import tech.pegasys.teku.ssz.backing.type.ListViewType;
import tech.pegasys.teku.ssz.backing.view.SszPrimitives.Bytes32View;
import tech.pegasys.teku.ssz.backing.view.SszListImpl;
import tech.pegasys.teku.ssz.backing.view.SszUtils;

public class BeaconBlocksByRootRequestMessage extends SszListImpl<Bytes32View>
    implements SszList<Bytes32View>, RpcRequest {

  private static final ListViewType<Bytes32View> LIST_VIEW_TYPE =
      new ListViewType<>(BasicViewTypes.BYTES32_TYPE, MAX_REQUEST_BLOCKS);

  public static class BeaconBlocksByRootRequestMessageType
      extends AbstractDelegateType<BeaconBlocksByRootRequestMessage> {

    private BeaconBlocksByRootRequestMessageType() {
      super(LIST_VIEW_TYPE);
    }

    @Override
    public BeaconBlocksByRootRequestMessage createFromBackingNode(TreeNode node) {
      return new BeaconBlocksByRootRequestMessage(node);
    }
  }

  public static final BeaconBlocksByRootRequestMessageType TYPE =
      new BeaconBlocksByRootRequestMessageType();

  public BeaconBlocksByRootRequestMessage(Iterable<Bytes32> roots) {
    super(SszUtils.toListView(LIST_VIEW_TYPE, roots, Bytes32View::new));
  }

  private BeaconBlocksByRootRequestMessage(TreeNode node) {
    super(LIST_VIEW_TYPE, node);
  }

  @Override
  public int getMaximumRequestChunks() {
    return size();
  }

  @Override
  public String toString() {
    return "BeaconBlocksByRootRequestMessage{" + super.toString() + "}";
  }
}
