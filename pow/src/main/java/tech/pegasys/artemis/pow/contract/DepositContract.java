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

package tech.pegasys.artemis.pow.contract;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * Auto generated code.
 *
 * <p><strong>Do not modify!</strong>
 *
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the <a
 * href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 4.3.0.
 */
public class DepositContract extends Contract {
  private static final String BINARY =
      "0x600035601c52740100000000000000000000000000000000000000006020526f7fffffffffffffffffffffffffffffff6040527fffffffffffffffffffffffffffffffff8000000000000000000000000000000060605274012a05f1fffffffffffffffffffffffffdabf41c006080527ffffffffffffffffffffffffed5fa0e000000000000000000000000000000000060a052341561009e57600080fd5b6101406000601f818352015b600061014051602081106100bd57600080fd5b600260c052602060c020015460208261016001015260208101905061014051602081106100e957600080fd5b600260c052602060c020015460208261016001015260208101905080610160526101609050602060c0825160208401600060025af161012757600080fd5b60c0519050606051600161014051018060405190131561014657600080fd5b809190121561015457600080fd5b6020811061016157600080fd5b600260c052602060c02001555b81516001018083528114156100aa575b50506112ff56600035601c52740100000000000000000000000000000000000000006020526f7fffffffffffffffffffffffffffffff6040527fffffffffffffffffffffffffffffffff8000000000000000000000000000000060605274012a05f1fffffffffffffffffffffffffdabf41c006080527ffffffffffffffffffffffffed5fa0e000000000000000000000000000000000060a052600015610277575b6101605261014052600061018052610140516101a0526101c060006008818352015b61018051600860008112156100da578060000360020a82046100e1565b8060020a82025b905090506101805260ff6101a051166101e052610180516101e0516101805101101561010c57600080fd5b6101e0516101805101610180526101a0517ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff86000811215610155578060000360020a820461015c565b8060020a82025b905090506101a0525b81516001018083528114156100bd575b50506018600860208206610200016020828401111561019357600080fd5b60208061022082610180600060046015f15050818152809050905090508051602001806102c0828460006004600a8704601201f16101d057600080fd5b50506102c05160206001820306601f82010390506103206102c0516008818352015b826103205111156102025761021e565b6000610320516102e001535b81516001018083528114156101f2575b50505060206102a05260406102c0510160206001820306601f8201039050610280525b6000610280511115156102535761026f565b602061028051036102a001516020610280510361028052610241565b610160515650005b63863a311b600051141561050857341561029057600080fd5b6000610140526101405161016052600154610180526101a060006020818352015b60016001610180511614156103325760006101a051602081106102d357600080fd5b600060c052602060c02001546020826102400101526020810190506101605160208261024001015260208101905080610240526102409050602060c0825160208401600060025af161032457600080fd5b60c0519050610160526103a0565b6000610160516020826101c00101526020810190506101a0516020811061035857600080fd5b600260c052602060c02001546020826101c0010152602081019050806101c0526101c09050602060c0825160208401600060025af161039657600080fd5b60c0519050610160525b61018060026103ae57600080fd5b60028151048152505b81516001018083528114156102b1575b505060006101605160208261044001015260208101905061014051610160516101805163806732896102c0526001546102e0526102e0516006580161009b565b506103405260006103a0525b6103405160206001820306601f82010390506103a0511015156104355761044e565b6103a05161036001526103a0516020016103a052610413565b61018052610160526101405261034060088060208461044001018260208501600060046012f150508051820191505060006018602082066103c0016020828401111561049957600080fd5b6020806103e082610140600060046015f150508181528090509050905060188060208461044001018260208501600060046014f150508051820191505080610440526104409050602060c0825160208401600060025af16104f957600080fd5b60c051905060005260206000f3005b63621fd130600051141561061a57341561052157600080fd5b63806732896101405260015461016052610160516006580161009b565b506101c0526000610220525b6101c05160206001820306601f82010390506102205110151561056c57610585565b610220516101e00152610220516020016102205261054a565b6101c0805160200180610280828460006004600a8704601201f16105a857600080fd5b50506102805160206001820306601f82010390506102e0610280516008818352015b826102e05111156105da576105f6565b60006102e0516102a001535b81516001018083528114156105ca575b5050506020610260526040610280510160206001820306601f8201039050610260f3005b63c47e300d600051141561117457606060046101403760506004356004016101a037603060043560040135111561065057600080fd5b604060243560040161022037602060243560040135111561067057600080fd5b608060443560040161028037606060443560040135111561069057600080fd5b63ffffffff600154106106a257600080fd5b633b9aca0061034052610340516106b857600080fd5b61034051340461032052633b9aca006103205110156106d657600080fd5b60306101a051146106e657600080fd5b602061022051146106f657600080fd5b6060610280511461070657600080fd5b6101a0516101c0516101e05161020051610220516102405161026051610280516102a0516102c0516102e05161030051610320516103405161036051610380516103a05163806732896103c052610320516103e0526103e0516006580161009b565b506104405260006104a0525b6104405160206001820306601f82010390506104a051101515610796576107af565b6104a05161046001526104a0516020016104a052610774565b6103a05261038052610360526103405261032052610300526102e0526102c0526102a05261028052610260526102405261022052610200526101e0526101c0526101a052610440805160200180610360828460006004600a8704601201f161081657600080fd5b50506101a0516101c0516101e05161020051610220516102405161026051610280516102a0516102c0516102e05161030051610320516103405161036051610380516103a0516103c0516103e05161040051610420516104405161046051610480516104a05163806732896104c0526001546104e0526104e0516006580161009b565b506105405260006105a0525b6105405160206001820306601f82010390506105a0511015156108c7576108e0565b6105a05161056001526105a0516020016105a0526108a5565b6104a05261048052610460526104405261042052610400526103e0526103c0526103a05261038052610360526103405261032052610300526102e0526102c0526102a05261028052610260526102405261022052610200526101e0526101c0526101a0526105408051602001806105c0828460006004600a8704601201f161096757600080fd5b505060a06106405261064051610680526101a08051602001806106405161068001828460006004600a8704601201f161099f57600080fd5b505061064051610680015160206001820306601f8201039050610640516106800161062081516040818352015b83610620511015156109dd576109fa565b6000610620516020850101535b81516001018083528114156109cc575b50505050602061064051610680015160206001820306601f820103905061064051010161064052610640516106a0526102208051602001806106405161068001828460006004600a8704601201f1610a5157600080fd5b505061064051610680015160206001820306601f8201039050610640516106800161062081516020818352015b8361062051101515610a8f57610aac565b6000610620516020850101535b8151600101808352811415610a7e575b50505050602061064051610680015160206001820306601f820103905061064051010161064052610640516106c0526103608051602001806106405161068001828460006004600a8704601201f1610b0357600080fd5b505061064051610680015160206001820306601f8201039050610640516106800161062081516020818352015b8361062051101515610b4157610b5e565b6000610620516020850101535b8151600101808352811415610b30575b50505050602061064051610680015160206001820306601f820103905061064051010161064052610640516106e0526102808051602001806106405161068001828460006004600a8704601201f1610bb557600080fd5b505061064051610680015160206001820306601f8201039050610640516106800161062081516060818352015b8361062051101515610bf357610c10565b6000610620516020850101535b8151600101808352811415610be2575b50505050602061064051610680015160206001820306601f82010390506106405101016106405261064051610700526105c08051602001806106405161068001828460006004600a8704601201f1610c6757600080fd5b505061064051610680015160206001820306601f8201039050610640516106800161062081516020818352015b8361062051101515610ca557610cc2565b6000610620516020850101535b8151600101808352811415610c94575b50505050602061064051610680015160206001820306601f8201039050610640510101610640527f649bbc62d0e31342afea4e5cd82d4049e7e1ee912fc0889aa790803be39038c561064051610680a160006107205260006101a06030806020846107e001018260208501600060046016f150508051820191505060006010602082066107600160208284011115610d5957600080fd5b60208061078082610720600060046015f15050818152809050905090506010806020846107e001018260208501600060046013f1505080518201915050806107e0526107e09050602060c0825160208401600060025af1610db957600080fd5b60c0519050610740526000600060406020820661088001610280518284011115610de257600080fd5b6060806108a0826020602088068803016102800160006004601bf1505081815280905090509050602060c0825160208401600060025af1610e2257600080fd5b60c0519050602082610a800101526020810190506000604060206020820661094001610280518284011115610e5657600080fd5b606080610960826020602088068803016102800160006004601bf1505081815280905090509050602080602084610a0001018260208501600060046015f150508051820191505061072051602082610a0001015260208101905080610a0052610a009050602060c0825160208401600060025af1610ed357600080fd5b60c0519050602082610a8001015260208101905080610a8052610a809050602060c0825160208401600060025af1610f0a57600080fd5b60c0519050610860526000600061074051602082610b20010152602081019050610220602080602084610b2001018260208501600060046015f150508051820191505080610b2052610b209050602060c0825160208401600060025af1610f7057600080fd5b60c0519050602082610ca00101526020810190506000610360600880602084610c2001018260208501600060046012f15050805182019150506000601860208206610ba00160208284011115610fc557600080fd5b602080610bc082610720600060046015f1505081815280905090509050601880602084610c2001018260208501600060046014f150508051820191505061086051602082610c2001015260208101905080610c2052610c209050602060c0825160208401600060025af161103857600080fd5b60c0519050602082610ca001015260208101905080610ca052610ca09050602060c0825160208401600060025af161106f57600080fd5b60c0519050610b0052600180546001825401101561108c57600080fd5b6001815401815550600154610d2052610d4060006020818352015b60016001610d20511614156110dc57610b0051610d4051602081106110cb57600080fd5b600060c052602060c0200155611170565b6000610d4051602081106110ef57600080fd5b600060c052602060c0200154602082610d60010152602081019050610b0051602082610d6001015260208101905080610d6052610d609050602060c0825160208401600060025af161114057600080fd5b60c0519050610b0052610d20600261115757600080fd5b60028151048152505b81516001018083528114156110a7575b5050005b60006000fd5b6101856112ff036101856000396101856112ff036000f3";

  public static final String FUNC_GET_HASH_TREE_ROOT = "get_hash_tree_root";

  public static final String FUNC_GET_DEPOSIT_COUNT = "get_deposit_count";

  public static final String FUNC_DEPOSIT = "deposit";

  public static final Event DEPOSITEVENT_EVENT =
      new Event(
          "DepositEvent",
          Arrays.<TypeReference<?>>asList(
              new TypeReference<DynamicBytes>() {},
              new TypeReference<DynamicBytes>() {},
              new TypeReference<DynamicBytes>() {},
              new TypeReference<DynamicBytes>() {},
              new TypeReference<DynamicBytes>() {}));;

  @Deprecated
  protected DepositContract(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  protected DepositContract(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
  }

  @Deprecated
  protected DepositContract(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  protected DepositContract(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public List<DepositEventEventResponse> getDepositEventEvents(
      TransactionReceipt transactionReceipt) {
    List<Contract.EventValuesWithLog> valueList =
        extractEventParametersWithLog(DEPOSITEVENT_EVENT, transactionReceipt);
    ArrayList<DepositEventEventResponse> responses =
        new ArrayList<DepositEventEventResponse>(valueList.size());
    for (Contract.EventValuesWithLog eventValues : valueList) {
      DepositEventEventResponse typedResponse = new DepositEventEventResponse();
      typedResponse.log = eventValues.getLog();
      typedResponse.pubkey = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
      typedResponse.withdrawal_credentials =
          (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
      typedResponse.amount = (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
      typedResponse.signature = (byte[]) eventValues.getNonIndexedValues().get(3).getValue();
      typedResponse.index = (byte[]) eventValues.getNonIndexedValues().get(4).getValue();
      responses.add(typedResponse);
    }
    return responses;
  }

  public Flowable<DepositEventEventResponse> depositEventEventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new Function<Log, DepositEventEventResponse>() {
              @Override
              public DepositEventEventResponse apply(Log log) {
                Contract.EventValuesWithLog eventValues =
                    extractEventParametersWithLog(DEPOSITEVENT_EVENT, log);
                DepositEventEventResponse typedResponse = new DepositEventEventResponse();
                typedResponse.log = log;
                typedResponse.pubkey = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
                typedResponse.withdrawal_credentials =
                    (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
                typedResponse.amount = (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
                typedResponse.signature =
                    (byte[]) eventValues.getNonIndexedValues().get(3).getValue();
                typedResponse.index = (byte[]) eventValues.getNonIndexedValues().get(4).getValue();
                return typedResponse;
              }
            });
  }

  public Flowable<DepositEventEventResponse> depositEventEventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(DEPOSITEVENT_EVENT));
    return depositEventEventFlowable(filter);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public RemoteCall<byte[]> get_hash_tree_root() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_GET_HASH_TREE_ROOT,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
    return executeRemoteCallSingleValueReturn(function, byte[].class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public RemoteCall<byte[]> get_deposit_count() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_GET_DEPOSIT_COUNT,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}));
    return executeRemoteCallSingleValueReturn(function, byte[].class);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  public RemoteCall<TransactionReceipt> deposit(
      byte[] pubkey, byte[] withdrawal_credentials, byte[] signature, BigInteger weiValue) {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_DEPOSIT,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.DynamicBytes(pubkey),
                new org.web3j.abi.datatypes.DynamicBytes(withdrawal_credentials),
                new org.web3j.abi.datatypes.DynamicBytes(signature)),
            Collections.<TypeReference<?>>emptyList());
    return executeRemoteCallTransaction(function, weiValue);
  }

  @Deprecated
  public static DepositContract load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new DepositContract(contractAddress, web3j, credentials, gasPrice, gasLimit);
  }

  @Deprecated
  public static DepositContract load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return new DepositContract(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
  }

  public static DepositContract load(
      String contractAddress,
      Web3j web3j,
      Credentials credentials,
      ContractGasProvider contractGasProvider) {
    return new DepositContract(contractAddress, web3j, credentials, contractGasProvider);
  }

  public static DepositContract load(
      String contractAddress,
      Web3j web3j,
      TransactionManager transactionManager,
      ContractGasProvider contractGasProvider) {
    return new DepositContract(contractAddress, web3j, transactionManager, contractGasProvider);
  }

  public static RemoteCall<DepositContract> deploy(
      Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
    return deployRemoteCall(
        DepositContract.class, web3j, credentials, contractGasProvider, BINARY, "");
  }

  public static RemoteCall<DepositContract> deploy(
      Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
    return deployRemoteCall(
        DepositContract.class, web3j, transactionManager, contractGasProvider, BINARY, "");
  }

  @Deprecated
  public static RemoteCall<DepositContract> deploy(
      Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
    return deployRemoteCall(
        DepositContract.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
  }

  @Deprecated
  public static RemoteCall<DepositContract> deploy(
      Web3j web3j,
      TransactionManager transactionManager,
      BigInteger gasPrice,
      BigInteger gasLimit) {
    return deployRemoteCall(
        DepositContract.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
  }

  public static class DepositEventEventResponse {
    public Log log;

    public byte[] pubkey;

    public byte[] withdrawal_credentials;

    public byte[] amount;

    public byte[] signature;

    public byte[] index;
  }
}
