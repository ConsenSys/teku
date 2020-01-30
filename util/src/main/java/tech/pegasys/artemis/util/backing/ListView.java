package tech.pegasys.artemis.util.backing;

public interface ListView<C> extends CompositeView<C> {

  @Override
  int size();

  default void append(C value) {
    set(size(), value);
  }
}
