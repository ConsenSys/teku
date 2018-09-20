package net.consensys.pantheon.ethereum.rlp;


import java.util.function.IntUnaryOperator;
import java.util.function.LongUnaryOperator;

/**
 * Helper static methods to facilitate RLP decoding <b>within this package</b>. Neither this class
 * nor any of its method are meant to be exposed publicly, they are too low level.
 */
class RLPDecodingHelpers {

  /** The kind of items an RLP item can be. */
  enum Kind {
    BYTE_ELEMENT,
    SHORT_ELEMENT,
    LONG_ELEMENT,
    SHORT_LIST,
    LONG_LIST;

    static Kind of(int prefix) {
      if (prefix <= 0x7F) {
        return Kind.BYTE_ELEMENT;
      } else if (prefix <= 0xb7) {
        return Kind.SHORT_ELEMENT;
      } else if (prefix <= 0xbf) {
        return Kind.LONG_ELEMENT;
      } else if (prefix <= 0xf7) {
        return Kind.SHORT_LIST;
      } else {
        return Kind.LONG_LIST;
      }
    }

    boolean isList() {
      switch (this) {
        case SHORT_LIST:
        case LONG_LIST:
          return true;
        default:
          return false;
      }
    }
  }

  /** Read from the provided offset a size of the provided length, assuming this is enough bytes. */
  static int extractSize(IntUnaryOperator getter, int offset, int sizeLength) {
    int res = 0;
    int shift = 0;
    for (int i = 0; i < sizeLength; i++) {
      res |= (getter.applyAsInt(offset + (sizeLength - 1) - i) & 0xFF) << shift;
      shift += 8;
    }
    return res;
  }

  /** Read from the provided offset a size of the provided length, assuming this is enough bytes. */
  static int extractSizeFromLong(LongUnaryOperator getter, long offset, int sizeLength) {
    long res = 0;
    int shift = 0;
    for (int i = 0; i < sizeLength; i++) {
      res |= (getter.applyAsLong(offset + (sizeLength - 1) - i) & 0xFF) << shift;
      shift += 8;
    }
    try {
      return Math.toIntExact(res);
    } catch (ArithmeticException e) {
      String msg =
          "unable to extract size from long at offset " + offset + ", sizeLen=" + sizeLength;
      throw new RLPException(msg, e);
    }
  }
}
