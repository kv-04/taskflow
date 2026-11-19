package dev.taskflow.application;

/** Proves the test double honours the repository contract, so tests built on it are trustworthy. */
class InMemoryTaskRepositoryTest extends TaskRepositoryContractTest {
  @Override
  protected TaskRepository newRepository() {
    return new InMemoryTaskRepository();
  }
}
