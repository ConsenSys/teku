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

package tech.pegasys.teku.infrastructure.unsigned;

import static com.google.common.base.Preconditions.checkArgument;

import java.math.BigInteger;

public final class UInt64 implements Comparable<UInt64> {

  private static final long UNSIGNED_MASK = 0x7fffffffffffffffL;

  public static final UInt64 ZERO = new UInt64(0);
  public static final UInt64 ONE = new UInt64(1);
  public static final UInt64 MAX_VALUE = new UInt64(-1L);

  /**
   * Square root of the maximum uint64 value. Any two values less than or equal to this can be
   * safely multiplied together without overflowing a long.
   *
   * <p>If either value is greater than this number, overflow checks must be performed.
   */
  static final long SQRT_MAX_VALUE = 4294967295L;

  private final long value;

  private UInt64(final long value) {
    this.value = value;
  }

  public static UInt64 valueOf(final long value) {
    checkPositive(value);
    return fromLongBits(value);
  }

  public static UInt64 valueOf(final String value) {
    return fromLongBits(Long.parseUnsignedLong(value));
  }

  public static UInt64 valueOf(final BigInteger value) {
    checkArgument(
        value.signum() >= 0 && value.bitLength() <= Long.SIZE,
        "value (%s) is outside the range for uint64",
        value);
    return fromLongBits(value.longValue());
  }

  public static UInt64 fromLongBits(final long value) {
    return value == 0 ? ZERO : new UInt64(value);
  }

  public UInt64 plus(final long other) {
    checkPositive(other);
    return plus(this.value, other);
  }

  public UInt64 plus(final UInt64 other) {
    return plus(value, other.value);
  }

  private UInt64 plus(final long longBits1, final long longBits2) {
    if (longBits1 != 0 && Long.compareUnsigned(longBits2, MAX_VALUE.longValue() - longBits1) > 0) {
      throw new ArithmeticException("uint64 overflow");
    }
    return fromLongBits(longBits1 + longBits2);
  }

  public UInt64 minus(final long other) {
    checkPositive(other);
    return minus(value, other);
  }

  public UInt64 minus(final UInt64 other) {
    return minus(value, other.value);
  }

  private UInt64 minus(final long longBits1, final long longBits2) {
    if (Long.compareUnsigned(longBits1, longBits2) < 0) {
      throw new ArithmeticException("uint64 underflow");
    }
    return fromLongBits(longBits1 - longBits2);
  }

  public UInt64 times(final long other) {
    checkPositive(other);
    return times(value, other);
  }

  public UInt64 times(final UInt64 other) {
    return times(value, other.value);
  }

  private UInt64 times(final long longBits1, final long longBits2) {
    if ((isSafeMultiplicand(longBits1) && isSafeMultiplicand(longBits2))
        // 0 and 1 do not increase the size so will not overflow regardless of the second number
        || longBits1 == 0
        || longBits1 == 1
        || longBits2 == 0
        || longBits2 == 1) {
      // We can use the fast method
      return fromLongBits(longBits1 * longBits2);
    }
    if (longBits1 < 0 || longBits2 < 0) {
      // Already in the upper half of the range so multiplying by anything except 0 or 1 overflows
      throw new ArithmeticException("uint64 overflow");
    }
    // Overflow is a possibility (but not guaranteed, use the slower approach)
    final BigInteger value1 = toUnsignedBigInteger(longBits1);
    final BigInteger value2 = toUnsignedBigInteger(longBits2);
    final BigInteger result = value1.multiply(value2);
    if (result.bitLength() > Long.SIZE) {
      throw new ArithmeticException("uint64 overflow");
    }
    return fromLongBits(result.longValue());
  }

  private boolean isSafeMultiplicand(final long longBits) {
    return Long.compareUnsigned(longBits, SQRT_MAX_VALUE) <= 0;
  }

  public UInt64 dividedBy(final long divisor) {
    checkPositive(divisor);
    return dividedBy(value, divisor);
  }

  public UInt64 dividedBy(final UInt64 divisor) {
    return dividedBy(value, divisor.value);
  }

  private UInt64 dividedBy(final long unsignedDividend, final long unsignedDivisor) {
    return fromLongBits(Long.divideUnsigned(unsignedDividend, unsignedDivisor));
  }

  public UInt64 mod(final long divisor) {
    checkPositive(divisor);
    return mod(value, divisor);
  }

  public UInt64 mod(final UInt64 divisor) {
    return mod(value, divisor.value);
  }

  private UInt64 mod(final long dividendBits, final long divisorBits) {
    return fromLongBits(Long.remainderUnsigned(dividendBits, divisorBits));
  }

  public UInt64 max(final UInt64 other) {
    return compareTo(other) >= 0 ? this : other;
  }

  public UInt64 min(final UInt64 other) {
    return compareTo(other) >= 0 ? other : this;
  }

  public long longValue() {
    return value;
  }

  public int intValue() {
    final int intValue = Math.toIntExact(value);
    if (intValue < 0) {
      throw new ArithmeticException("integer overflow");
    }
    return intValue;
  }

  public BigInteger bigIntegerValue() {
    return toUnsignedBigInteger(value);
  }

  // From Guava UnsignedLong.bigIntegerValue(). Apache 2 license.
  private static BigInteger toUnsignedBigInteger(final long value) {
    BigInteger bigInt = BigInteger.valueOf(value & UNSIGNED_MASK);
    if (value < 0) {
      bigInt = bigInt.setBit(Long.SIZE - 1);
    }
    return bigInt;
  }

  private static void checkPositive(final long other) {
    checkArgument(other >= 0, "value (%s) must be >= 0", other);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final UInt64 uInt64 = (UInt64) o;
    return value == uInt64.value;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(value);
  }

  @Override
  public String toString() {
    return Long.toUnsignedString(value);
  }

  @Override
  public int compareTo(final UInt64 o) {
    return Long.compareUnsigned(value, o.value);
  }
}
