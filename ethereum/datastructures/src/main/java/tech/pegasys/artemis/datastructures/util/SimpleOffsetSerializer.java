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

package tech.pegasys.artemis.datastructures.util;

import static tech.pegasys.artemis.datastructures.Constants.BYTES_PER_LENGTH_OFFSET;

import com.google.common.primitives.UnsignedLong;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.ssz.SSZ;
import org.apache.tuweni.ssz.SSZReader;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlock;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockBody;
import tech.pegasys.artemis.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.artemis.datastructures.blocks.Eth1Data;
import tech.pegasys.artemis.datastructures.operations.Attestation;
import tech.pegasys.artemis.datastructures.operations.AttestationData;
import tech.pegasys.artemis.datastructures.operations.AttestationDataAndCustodyBit;
import tech.pegasys.artemis.datastructures.operations.AttesterSlashing;
import tech.pegasys.artemis.datastructures.operations.Deposit;
import tech.pegasys.artemis.datastructures.operations.DepositData;
import tech.pegasys.artemis.datastructures.operations.IndexedAttestation;
import tech.pegasys.artemis.datastructures.operations.ProposerSlashing;
import tech.pegasys.artemis.datastructures.operations.Transfer;
import tech.pegasys.artemis.datastructures.operations.VoluntaryExit;
import tech.pegasys.artemis.datastructures.state.BeaconState;
import tech.pegasys.artemis.datastructures.state.Checkpoint;
import tech.pegasys.artemis.datastructures.state.CompactCommittee;
import tech.pegasys.artemis.datastructures.state.Crosslink;
import tech.pegasys.artemis.datastructures.state.Fork;
import tech.pegasys.artemis.datastructures.state.HistoricalBatch;
import tech.pegasys.artemis.datastructures.state.PendingAttestation;
import tech.pegasys.artemis.datastructures.state.Validator;
import tech.pegasys.artemis.util.SSZTypes.Bytes4;
import tech.pegasys.artemis.util.SSZTypes.SSZContainer;
import tech.pegasys.artemis.util.SSZTypes.SSZList;
import tech.pegasys.artemis.util.SSZTypes.SSZVector;
import tech.pegasys.artemis.util.bls.BLSPublicKey;
import tech.pegasys.artemis.util.bls.BLSSignature;
import tech.pegasys.artemis.util.reflectionInformation.ReflectionInformation;
import tech.pegasys.artemis.util.sos.SimpleOffsetSerializable;

@SuppressWarnings({"rawtypes", "unchecked"})
public class SimpleOffsetSerializer {

  public static HashMap<Class, ReflectionInformation> classReflectionInfo = new HashMap<>();

  static {
    classReflectionInfo.put(BeaconBlock.class, new ReflectionInformation(BeaconBlock.class));
    classReflectionInfo.put(
        BeaconBlockBody.class, new ReflectionInformation(BeaconBlockBody.class));
    classReflectionInfo.put(
        BeaconBlockHeader.class, new ReflectionInformation(BeaconBlockHeader.class));
    classReflectionInfo.put(Eth1Data.class, new ReflectionInformation(Eth1Data.class));
    classReflectionInfo.put(Attestation.class, new ReflectionInformation(Attestation.class));
    classReflectionInfo.put(
        AttestationData.class, new ReflectionInformation(AttestationData.class));
    classReflectionInfo.put(
        AttestationDataAndCustodyBit.class,
        new ReflectionInformation(AttestationDataAndCustodyBit.class));
    classReflectionInfo.put(
        AttesterSlashing.class, new ReflectionInformation(AttesterSlashing.class));
    classReflectionInfo.put(Deposit.class, new ReflectionInformation(Deposit.class));
    classReflectionInfo.put(DepositData.class, new ReflectionInformation(DepositData.class));
    classReflectionInfo.put(
        IndexedAttestation.class, new ReflectionInformation(IndexedAttestation.class));
    classReflectionInfo.put(
        ProposerSlashing.class, new ReflectionInformation(ProposerSlashing.class));
    classReflectionInfo.put(Transfer.class, new ReflectionInformation(Transfer.class));
    classReflectionInfo.put(VoluntaryExit.class, new ReflectionInformation(VoluntaryExit.class));
    classReflectionInfo.put(BeaconState.class, new ReflectionInformation(BeaconState.class));
    classReflectionInfo.put(Checkpoint.class, new ReflectionInformation(Checkpoint.class));
    classReflectionInfo.put(
        CompactCommittee.class, new ReflectionInformation(CompactCommittee.class));
    classReflectionInfo.put(Crosslink.class, new ReflectionInformation(Crosslink.class));
    classReflectionInfo.put(Fork.class, new ReflectionInformation(Fork.class));
    classReflectionInfo.put(
        HistoricalBatch.class, new ReflectionInformation(HistoricalBatch.class));
    classReflectionInfo.put(
        PendingAttestation.class, new ReflectionInformation(PendingAttestation.class));
    classReflectionInfo.put(Validator.class, new ReflectionInformation(Validator.class));
  }

  public static Bytes serialize(SimpleOffsetSerializable value) {
    // TODO assert sum(fixed_lengths + variable_lengths) < 2**(BYTES_PER_LENGTH_OFFSET *
    // BITS_PER_BYTE)
    // List<UnsignedLong> variable_lengths = new ArrayList<>();
    List<UnsignedLong> variable_offsets = new ArrayList<>();
    List<Bytes> interleaved_values = new ArrayList<>();
    UnsignedLong fixedLengthSum = UnsignedLong.ZERO;
    UnsignedLong varLengthSum = UnsignedLong.ZERO;

    // System.out.println("Fixed Part Size: " + value.get_fixed_parts().size());
    // System.out.println("Var Part Size: " + value.get_variable_parts().size());
    for (Bytes fixedPart : value.get_fixed_parts()) {
      UnsignedLong fixedPartSize = UnsignedLong.valueOf(fixedPart.size());
      if (fixedPartSize.equals(UnsignedLong.ZERO)) {
        fixedPartSize = UnsignedLong.valueOf(4L);
      }
      fixedLengthSum = fixedLengthSum.plus(fixedPartSize);
    }

    variable_offsets.add(fixedLengthSum);
    for (Bytes varPart : value.get_variable_parts()) {
      UnsignedLong varPartSize = UnsignedLong.valueOf(varPart.size());
      varLengthSum = varLengthSum.plus(varPartSize);
      variable_offsets.add(fixedLengthSum.plus(varLengthSum));
    }

    int interleavingIndex = 0;
    for (Bytes element : value.get_fixed_parts()) {
      if (!element.equals(Bytes.EMPTY)) {
        interleaved_values.add(element);
      } else {
        interleaved_values.add(
            SSZ.encodeUInt32(variable_offsets.get(interleavingIndex).longValue()));
      }
      ++interleavingIndex;
    }

    return Bytes.wrap(
        Bytes.concatenate(interleaved_values.toArray(new Bytes[0])),
        Bytes.concatenate(value.get_variable_parts().toArray(new Bytes[0])));
  }

  public static Bytes serializeFixedCompositeList(List<? extends SimpleOffsetSerializable> values) {
    return Bytes.fromHexString(
        values.stream()
            .map(item -> serialize(item).toHexString().substring(2))
            .collect(Collectors.joining()));
  }

  public static Bytes serializeVariableCompositeList(
      List<? extends SimpleOffsetSerializable> values) {
    List<Bytes> parts =
        values.stream().map(SimpleOffsetSerializer::serialize).collect(Collectors.toList());
    List<UnsignedLong> fixed_lengths = Collections.nCopies(values.size(), BYTES_PER_LENGTH_OFFSET);
    List<Bytes> variable_parts = new ArrayList<>();
    List<Bytes> fixed_parts = new ArrayList<>();
    UnsignedLong offset = UnsignedLong.ZERO;
    for (UnsignedLong length : fixed_lengths) {
      offset = offset.plus(length);
    }
    for (Bytes part : parts) {
      fixed_parts.add(SSZ.encodeUInt32(offset.longValue()));
      variable_parts.add(part);
      offset = offset.plus(UnsignedLong.valueOf(part.size()));
    }
    return Bytes.wrap(
        Bytes.concatenate(fixed_parts.toArray(new Bytes[0])),
        Bytes.concatenate(variable_parts.toArray(new Bytes[0])));
  }

  @SuppressWarnings("rawtypes")
  public static Object deserializePrimitive(
      Class classInfo, SSZReader reader, MutableInt bytePointer) {
    switch (classInfo.getSimpleName()) {
      case "UnsignedLong":
        bytePointer.add(8);
        return UnsignedLong.fromLongBits(reader.readUInt64());
      case "ArrayWrappingBytes32":
      case "Bytes32":
        bytePointer.add(32);
        return Bytes32.wrap(reader.readFixedBytes(32));
      case "Bytes4":
        bytePointer.add(4);
        return new Bytes4(reader.readFixedBytes(4));
      case "BLSSignature":
        bytePointer.add(96);
        return BLSSignature.fromBytes(reader.readFixedBytes(96));
      case "BLSPublicKey":
        bytePointer.add(48);
        return BLSPublicKey.fromBytes(reader.readFixedBytes(48));
      case "boolean":
        bytePointer.add(1);
        return reader.readBoolean();
      default:
        return new Object();
    }
  }

  @SuppressWarnings("TypeParameterUnusedInFormals")
  public static <T> T deserialize(Bytes bytes, Class classInfo) {
    MutableInt bytePointer = new MutableInt(0);
    int endByte = bytes.size();
    return SSZ.decode(
        bytes,
        sszReader -> (T) deserializeVariableComposite(classInfo, sszReader, bytePointer, endByte));
  }

  @SuppressWarnings("TypeParameterUnusedInFormals")
  public static <T> T deserializeFixedComposite(
      Class classInfo, SSZReader reader, MutableInt bytePointer) {
    try {
      ReflectionInformation reflectionInformation = classReflectionInfo.get(classInfo);
      int numParams = reflectionInformation.getParameterCount();
      Field[] fields = reflectionInformation.getFields();
      List<Integer> vectorLengths = reflectionInformation.getVectorLengths();
      List<Class> vectorElementTypes = reflectionInformation.getVectorElementTypes();
      int vectorCounter = 0;
      Object[] params = new Object[numParams];
      for (int i = 0; i < numParams; i++) {
        Class fieldClass = fields[i].getType();
        Object fieldObject = null;
        if (isPrimitive(fieldClass)) {
          fieldObject = deserializePrimitive(fieldClass, reader, bytePointer);
        } else if (isContainer(fieldClass)) {
          fieldObject = deserializeFixedComposite(fieldClass, reader, bytePointer);
        } else if (isVector(fieldClass)) {
          fieldObject =
              deserializeFixedElementVector(
                  vectorElementTypes.get(vectorCounter),
                  vectorLengths.get(vectorCounter),
                  reader,
                  bytePointer);
          vectorCounter++;
        }

        if (fieldObject == null) {
          throw new DecoderException("Problem decoding class: " + classInfo.getSimpleName() + " ");
        }
        params[i] = fieldObject;
      }

      Constructor constructor = reflectionInformation.getConstructor();
      return (T) constructor.newInstance(params);
    } catch (Exception e) {
      System.out.println(e);
      return (T) new Object();
    }
  }

  private static SSZVector deserializeFixedElementVector(
      Class classInfo, int numElements, SSZReader reader, MutableInt bytePointer) {
    List newList = new ArrayList<>();
    for (int i = 0; i < numElements; i++) {
      if (isPrimitive(classInfo)) {
        newList.add(deserializePrimitive(classInfo, reader, bytePointer));
      } else if (isContainer(classInfo) && !classReflectionInfo.get(classInfo).isVariable()) {
        newList.add(deserializeFixedComposite(classInfo, reader, bytePointer));
      }
    }
    return new SSZVector<>(newList, classInfo);
  }

  @SuppressWarnings("TypeParameterUnusedInFormals")
  private static <T> T deserializeVariableComposite(
      Class classInfo, SSZReader reader, MutableInt bytesPointer, int bytesEndByte) {
    try {
      int currentObjectStartByte = bytesPointer.intValue();
      ReflectionInformation reflectionInformation = classReflectionInfo.get(classInfo);
      int numParams = reflectionInformation.getParameterCount();
      Field[] fields = reflectionInformation.getFields();
      List<Integer> vectorLengths = reflectionInformation.getVectorLengths();
      List<Class> vectorElementTypes = reflectionInformation.getVectorElementTypes();
      List<Integer> offsets = new ArrayList<>();
      List<Integer> variableFieldIndices = new ArrayList<>();
      int vectorCounter = 0;
      Object[] params = new Object[numParams];
      for (int i = 0; i < numParams; i++) {
        Class fieldClass = fields[i].getType();
        Object fieldObject = null;
        if (isVariable(fieldClass)) {
          variableFieldIndices.add(i);
          offsets.add(readOffset(reader, bytesPointer));
        } else if (isPrimitive(fieldClass)) {
          fieldObject = deserializePrimitive(fieldClass, reader, bytesPointer);
        } else if (isContainer(fieldClass)) {
          fieldObject = deserializeFixedComposite(fieldClass, reader, bytesPointer);
        } else if (isVector(fieldClass)) {
          fieldObject =
              deserializeFixedElementVector(
                  vectorElementTypes.get(vectorCounter),
                  vectorLengths.get(vectorCounter),
                  reader,
                  bytesPointer);
          vectorCounter++;
        }
        params[i] = fieldObject;
      }

      int variableObjectCounter = 0;
      for (Integer variableFieldIndex : variableFieldIndices) {
        Class fieldClass = fields[variableFieldIndex].getType();

        int currentObjectEndByte =
            (variableObjectCounter + 1) == variableFieldIndices.size()
                ? bytesEndByte
                : currentObjectStartByte + offsets.get(variableObjectCounter + 1);
        Object fieldObject = null;
        if (fieldClass == SSZList.class) {
          Class listElementType =
              reflectionInformation.getListElementTypes().get(variableObjectCounter);
          Long listElementMaxSize =
              reflectionInformation.getListElementMaxSizes().get(variableObjectCounter);
          List newSSZList = new SSZList(listElementType, listElementMaxSize);
          if (!isVariable(listElementType)) {
            while (bytesPointer.intValue() < currentObjectEndByte) {
              if (isContainer(listElementType)) {
                newSSZList.add(deserializeFixedComposite(listElementType, reader, bytesPointer));
              } else if (isPrimitive(listElementType)) {
                newSSZList.add(deserializePrimitive(listElementType, reader, bytesPointer));
              }
            }
            fieldObject = newSSZList;
          } else {

          }
        } else if (isContainer(fieldClass)) {
          fieldObject =
              deserializeVariableComposite(fieldClass, reader, bytesPointer, currentObjectEndByte);
        }
        variableObjectCounter++;
        params[variableFieldIndex] = fieldObject;
      }

      Constructor constructor = reflectionInformation.getConstructor();
      return (T) constructor.newInstance(params);
    } catch (Exception e) {
      System.out.println(e);
      return (T) new Object();
    }
  }

  /*
  private static SSZList deserializeFixedElementList(
          Bytes bytes, Class classInfo, int numElements, SSZReader reader) {


  }
  */

  private static int readOffset(SSZReader reader, MutableInt bytesPointer) {
    bytesPointer.add(4);
    return reader.readInt32();
  }

  private static boolean isVariable(Class classInfo) {
    if (classInfo == SSZList.class) {
      return true;
    } else if (classReflectionInfo.get(classInfo) != null) {
      return classReflectionInfo.get(classInfo).isVariable();
    } else {
      return false;
    }
  }

  private static boolean isPrimitive(Class classInfo) {
    return !(SSZContainer.class.isAssignableFrom(classInfo) || classInfo == SSZVector.class);
  }

  private static boolean isVector(Class classInfo) {
    return classInfo == SSZVector.class;
  }

  private static boolean isContainer(Class classInfo) {
    return SSZContainer.class.isAssignableFrom(classInfo);
  }
}
