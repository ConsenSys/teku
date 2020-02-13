package tech.pegasys.artemis.util.SSZTypes;

import java.util.AbstractList;
import java.util.function.Function;
import tech.pegasys.artemis.util.backing.VectorViewWrite;
import tech.pegasys.artemis.util.backing.ViewRead;

public class SSZBasicBackingVector<C, R extends ViewRead> extends AbstractList<C>
    implements SSZVectorWrite<C> {

  private final Class<C> classInfo;
  private final VectorViewWrite<R> delegate;
  private final Function<C, R> wrapper;
  private final Function<R, C> unwrapper;

  public SSZBasicBackingVector(Class<C> classInfo,
      VectorViewWrite<R> delegate, Function<C, R> wrapper,
      Function<R, C> unwrapper) {
    this.classInfo = classInfo;
    this.delegate = delegate;
    this.wrapper = wrapper;
    this.unwrapper = unwrapper;
  }

  @Override
  public C get(int index) {
    return unwrapper.apply(delegate.get(index));
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public Class<C> getElementType() {
    return classInfo;
  }

  @Override
  public C set(int index, C element) {
    return unwrapper.apply(delegate.set(index, wrapper.apply(element)));
  }

}
