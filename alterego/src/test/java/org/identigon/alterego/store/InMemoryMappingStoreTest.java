package org.identigon.alterego.store;

/** M4's own {@link MappingStore} implementation, exercised via the reusable contract tests. */
class InMemoryMappingStoreTest extends MappingStoreContractTest {

  @Override
  protected MappingStore createStore() {
    return new InMemoryMappingStore();
  }
}
