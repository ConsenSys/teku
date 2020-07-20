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

package tech.pegasys.teku.pow.contract;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
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
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.BaseEventResponse;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import tech.pegasys.teku.infrastructure.async.SafeFuture;

/**
 * Auto generated code.
 *
 * <p><strong>Do not modify!</strong>
 *
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the <a
 * href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 4.5.5.
 */
@SuppressWarnings("rawtypes")
public class DepositContract extends Contract {
  private static final String BINARY =
      "0x740100000000000000000000000000000000000000006020526f7fffffffffffffffffffffffffffffff6040527fffffffffffffffffffffffffffffffff8000000000000000000000000000000060605274012a05f1fffffffffffffffffffffffffdabf41c006080527ffffffffffffffffffffffffed5fa0e000000000000000000000000000000000060a052341561009857600080fd5b6101406000601f818352015b600061014051602081106100b757600080fd5b600260c052602060c020015460208261016001015260208101905061014051602081106100e357600080fd5b600260c052602060c020015460208261016001015260208101905080610160526101609050602060c0825160208401600060025af161012157600080fd5b60c0519050606051600161014051018060405190131561014057600080fd5b809190121561014e57600080fd5b6020811061015b57600080fd5b600260c052602060c02001555b81516001018083528114156100a4575b50506111d656600035601c52740100000000000000000000000000000000000000006020526f7fffffffffffffffffffffffffffffff6040527fffffffffffffffffffffffffffffffff8000000000000000000000000000000060605274012a05f1fffffffffffffffffffffffffdabf41c006080527ffffffffffffffffffffffffed5fa0e000000000000000000000000000000000060a052600015610265575b6101605261014052600061018052610140516101a0526101c060006008818352015b61018051600860008112156100da578060000360020a82046100e1565b8060020a82025b905090506101805260ff6101a051166101e052610180516101e0516101805101101561010c57600080fd5b6101e0516101805101610180526101a0517ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff86000811215610155578060000360020a820461015c565b8060020a82025b905090506101a0525b81516001018083528114156100bd575b50506018600860208206610200016020828401111561019357600080fd5b60208061022082610180600060046015f15050818152809050905090508051602001806102c0828460006004600a8704601201f16101d057600080fd5b50506103206102c0516020818352015b60206103205111156101f15761020d565b6000610320516102e001535b81516001018083528114156101e0575b505060206102a05260406102c0510160206001820306601f8201039050610280525b6000610280511115156102415761025d565b602061028051036102a00151602061028051036102805261022f565b610160515650005b63c5f2892f60005114156104f757341561027e57600080fd5b6000610140526101405161016052600154610180526101a060006020818352015b60016001610180511614156103205760006101a051602081106102c157600080fd5b600060c052602060c02001546020826102400101526020810190506101605160208261024001015260208101905080610240526102409050602060c0825160208401600060025af161031257600080fd5b60c05190506101605261038e565b6000610160516020826101c00101526020810190506101a0516020811061034657600080fd5b600260c052602060c02001546020826101c0010152602081019050806101c0526101c09050602060c0825160208401600060025af161038457600080fd5b60c0519050610160525b610180600261039c57600080fd5b60028151048152505b815160010180835281141561029f575b505060006101605160208261046001015260208101905061014051610160516101805163806732896102e05260015461030052610300516006580161009b565b506103605260006103c0525b6103605160206001820306601f82010390506103c0511015156104235761043c565b6103c05161038001526103c0516020016103c052610401565b61018052610160526101405261036060088060208461046001018260208501600060046012f150508051820191505060006018602082066103e0016020828401111561048757600080fd5b60208061040082610140600060046015f150508181528090509050905060188060208461046001018260208501600060046014f150508051820191505080610460526104609050602060c0825160208401600060025af16104e757600080fd5b60c051905060005260206000f350005b63621fd13060005114156105f857341561051057600080fd5b63806732896101405260015461016052610160516006580161009b565b506101c0526000610220525b6101c05160206001820306601f82010390506102205110151561055b57610574565b610220516101e001526102205160200161022052610539565b6101c0805160200180610280828460006004600a8704601201f161059757600080fd5b50506102e0610280516020818352015b60206102e05111156105b8576105d4565b60006102e0516102a001535b81516001018083528114156105a7575b50506020610260526040610280510160206001820306601f8201039050610260f350005b6322895118600051141561105157605060043560040161014037603060043560040135111561062657600080fd5b60406024356004016101c037602060243560040135111561064657600080fd5b608060443560040161022037606060443560040135111561066657600080fd5b63ffffffff6001541061067857600080fd5b633b9aca006102e0526102e05161068e57600080fd5b6102e05134046102c052633b9aca006102c05110156106ac57600080fd5b603061014051146106bc57600080fd5b60206101c051146106cc57600080fd5b606061022051146106dc57600080fd5b610140610360525b61036051516020610360510161036052610360610360511015610706576106e4565b6380673289610380526102c0516103a0526103a0516006580161009b565b50610400526000610460525b6104005160206001820306601f8201039050610460511015156107525761076b565b6104605161042001526104605160200161046052610730565b610340610360525b610360515260206103605103610360526101406103605110151561079657610773565b610400805160200180610300828460006004600a8704601201f16107b957600080fd5b5050610140610480525b610480515160206104805101610480526104806104805110156107e5576107c3565b63806732896104a0526001546104c0526104c0516006580161009b565b50610520526000610580525b6105205160206001820306601f82010390506105805110151561083057610849565b610580516105400152610580516020016105805261080e565b610460610480525b610480515260206104805103610480526101406104805110151561087457610851565b6105208051602001806105a0828460006004600a8704601201f161089757600080fd5b505060a06106205261062051610660526101408051602001806106205161066001828460006004600a8704601201f16108cf57600080fd5b5050610600610620516106600151610240818352015b6102406106005111156108f757610918565b600061060051610620516106800101535b81516001018083528114156108e5575b5050602061062051610660015160206001820306601f82010390506106205101016106205261062051610680526101c08051602001806106205161066001828460006004600a8704601201f161096d57600080fd5b5050610600610620516106600151610240818352015b610240610600511115610995576109b6565b600061060051610620516106800101535b8151600101808352811415610983575b5050602061062051610660015160206001820306601f820103905061062051010161062052610620516106a0526103008051602001806106205161066001828460006004600a8704601201f1610a0b57600080fd5b5050610600610620516106600151610240818352015b610240610600511115610a3357610a54565b600061060051610620516106800101535b8151600101808352811415610a21575b5050602061062051610660015160206001820306601f820103905061062051010161062052610620516106c0526102208051602001806106205161066001828460006004600a8704601201f1610aa957600080fd5b5050610600610620516106600151610240818352015b610240610600511115610ad157610af2565b600061060051610620516106800101535b8151600101808352811415610abf575b5050602061062051610660015160206001820306601f820103905061062051010161062052610620516106e0526105a08051602001806106205161066001828460006004600a8704601201f1610b4757600080fd5b5050610600610620516106600151610240818352015b610240610600511115610b6f57610b90565b600061060051610620516106800101535b8151600101808352811415610b5d575b5050602061062051610660015160206001820306601f8201039050610620510101610620527f649bbc62d0e31342afea4e5cd82d4049e7e1ee912fc0889aa790803be39038c561062051610660a160006107005260006101406030806020846107c001018260208501600060046016f150508051820191505060006010602082066107400160208284011115610c2557600080fd5b60208061076082610700600060046015f15050818152809050905090506010806020846107c001018260208501600060046013f1505080518201915050806107c0526107c09050602060c0825160208401600060025af1610c8557600080fd5b60c0519050610720526000600060406020820661086001610220518284011115610cae57600080fd5b606080610880826020602088068803016102200160006004601bf1505081815280905090509050602060c0825160208401600060025af1610cee57600080fd5b60c0519050602082610a600101526020810190506000604060206020820661092001610220518284011115610d2257600080fd5b606080610940826020602088068803016102200160006004601bf15050818152809050905090506020806020846109e001018260208501600060046015f1505080518201915050610700516020826109e0010152602081019050806109e0526109e09050602060c0825160208401600060025af1610d9f57600080fd5b60c0519050602082610a6001015260208101905080610a6052610a609050602060c0825160208401600060025af1610dd657600080fd5b60c0519050610840526000600061072051602082610b000101526020810190506101c0602080602084610b0001018260208501600060046015f150508051820191505080610b0052610b009050602060c0825160208401600060025af1610e3c57600080fd5b60c0519050602082610c800101526020810190506000610300600880602084610c0001018260208501600060046012f15050805182019150506000601860208206610b800160208284011115610e9157600080fd5b602080610ba082610700600060046015f1505081815280905090509050601880602084610c0001018260208501600060046014f150508051820191505061084051602082610c0001015260208101905080610c0052610c009050602060c0825160208401600060025af1610f0457600080fd5b60c0519050602082610c8001015260208101905080610c8052610c809050602060c0825160208401600060025af1610f3b57600080fd5b60c0519050610ae052606435610ae05114610f5557600080fd5b6001805460018254011015610f6957600080fd5b6001815401815550600154610d0052610d2060006020818352015b60016001610d0051161415610fb957610ae051610d205160208110610fa857600080fd5b600060c052602060c020015561104d565b6000610d205160208110610fcc57600080fd5b600060c052602060c0200154602082610d40010152602081019050610ae051602082610d4001015260208101905080610d4052610d409050602060c0825160208401600060025af161101d57600080fd5b60c0519050610ae052610d00600261103457600080fd5b60028151048152505b8151600101808352811415610f84575b5050005b60006000fd5b61017f6111d60361017f60003961017f6111d6036000f3";

  public static final String FUNC_GET_DEPOSIT_ROOT = "get_deposit_root";

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

  public SafeFuture<List<DepositEventEventResponse>> depositEventInRange(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    final EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(DEPOSITEVENT_EVENT));
    return SafeFuture.of(
        web3j
            .ethGetLogs(filter)
            .sendAsync()
            .thenApply(
                logs ->
                    logs.getLogs().stream()
                        .map(log -> (Log) log.get())
                        .map(this::convertLogToDepositEventEventResponse)
                        .collect(Collectors.toList())));
  }

  private DepositEventEventResponse convertLogToDepositEventEventResponse(final Log log) {
    EventValuesWithLog eventValues = extractEventParametersWithLog(DEPOSITEVENT_EVENT, log);
    DepositEventEventResponse typedResponse = new DepositEventEventResponse();
    typedResponse.log = log;
    typedResponse.pubkey = (byte[]) eventValues.getNonIndexedValues().get(0).getValue();
    typedResponse.withdrawal_credentials =
        (byte[]) eventValues.getNonIndexedValues().get(1).getValue();
    typedResponse.amount = (byte[]) eventValues.getNonIndexedValues().get(2).getValue();
    typedResponse.signature = (byte[]) eventValues.getNonIndexedValues().get(3).getValue();
    typedResponse.index = (byte[]) eventValues.getNonIndexedValues().get(4).getValue();
    return typedResponse;
  }

  public Flowable<DepositEventEventResponse> depositEventEventFlowable(EthFilter filter) {
    return web3j
        .ethLogFlowable(filter)
        .map(
            new Function<Log, DepositEventEventResponse>() {
              @Override
              public DepositEventEventResponse apply(Log log) {
                return convertLogToDepositEventEventResponse(log);
              }
            });
  }

  public Flowable<DepositEventEventResponse> depositEventEventFlowable(
      DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
    EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
    filter.addSingleTopic(EventEncoder.encode(DEPOSITEVENT_EVENT));
    return depositEventEventFlowable(filter);
  }

  public RemoteFunctionCall<byte[]> get_deposit_root() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_GET_DEPOSIT_ROOT,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}));
    return executeRemoteCallSingleValueReturn(function, byte[].class);
  }

  public RemoteFunctionCall<byte[]> get_deposit_count() {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_GET_DEPOSIT_COUNT,
            Arrays.<Type>asList(),
            Arrays.<TypeReference<?>>asList(new TypeReference<DynamicBytes>() {}));
    return executeRemoteCallSingleValueReturn(function, byte[].class);
  }

  public RemoteFunctionCall<TransactionReceipt> deposit(
      byte[] pubkey,
      byte[] withdrawal_credentials,
      byte[] signature,
      byte[] deposit_data_root,
      BigInteger weiValue) {
    final org.web3j.abi.datatypes.Function function =
        new org.web3j.abi.datatypes.Function(
            FUNC_DEPOSIT,
            Arrays.<Type>asList(
                new org.web3j.abi.datatypes.DynamicBytes(pubkey),
                new org.web3j.abi.datatypes.DynamicBytes(withdrawal_credentials),
                new org.web3j.abi.datatypes.DynamicBytes(signature),
                new org.web3j.abi.datatypes.generated.Bytes32(deposit_data_root)),
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

  public static class DepositEventEventResponse extends BaseEventResponse {
    public byte[] pubkey;

    public byte[] withdrawal_credentials;

    public byte[] amount;

    public byte[] signature;

    public byte[] index;
  }
}
