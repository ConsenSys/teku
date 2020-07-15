package tech.pegasys.teku.bls.supra;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.supra.swig.blst;
import tech.pegasys.teku.bls.supra.swig.p1;
import tech.pegasys.teku.bls.supra.swig.p1_affine;

public class PublicKey {
  private static final int COMPRESSED_PK_SIZE = 48;
  private static final int UNCOMPRESSED_PK_LENGTH = 49;

  public static PublicKey fromBytes(Bytes compressed) {
    checkArgument(
        compressed.size() == COMPRESSED_PK_SIZE,
        "Expected " + COMPRESSED_PK_SIZE + " bytes of input but got %s",
        compressed.size());
    p1_affine ecPoint = new p1_affine();
    blst.p1_uncompress(ecPoint, compressed.toArrayUnsafe());
    return new PublicKey(ecPoint);
  }

  public static PublicKey aggregate(List<PublicKey> publicKeys) {
    checkArgument(publicKeys.size() > 0);

    p1 sum = new p1();
    blst.p1_from_affine(sum, publicKeys.get(0).ecPoint);
    for (int i = 1; i < publicKeys.size(); i++) {
      blst.p1_add_affine(sum, sum, publicKeys.get(i).ecPoint);
    }
    p1_affine res = new p1_affine();
    blst.p1_to_affine(res, sum);
    sum.delete();

    return new PublicKey(res);
  }

  public final p1_affine ecPoint;

  public PublicKey(p1_affine ecPoint) {
    this.ecPoint = ecPoint;
  }

  public Bytes toBytes() {
    byte[] res = new byte[COMPRESSED_PK_SIZE];
    blst.p1_affine_compress(res, ecPoint);
    return Bytes.wrap(res);
  }
}
